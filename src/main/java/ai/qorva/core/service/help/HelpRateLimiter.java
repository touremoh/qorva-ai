package ai.qorva.core.service.help;

import ai.qorva.core.config.HelpProperties;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Qorva Help is free, so these counters are what bounds its cost: per user per hour and per day, per tenant per
 * day, a tighter daily cap for demo users, and support tickets per user per day. Fixed windows (the clock hour,
 * the UTC day), in memory: per instance and reset by a restart, which is acceptable while the API runs as one.
 */
@Component
public class HelpRateLimiter {

	private final HelpProperties properties;
	private final Clock clock;
	private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
		.expireAfterWrite(Duration.ofHours(25))
		.maximumSize(200_000)
		.build();

	@Autowired
	public HelpRateLimiter(HelpProperties properties) {
		this(properties, Clock.systemUTC());
	}

	HelpRateLimiter(HelpProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	/** Counts one question, or refuses it (429) with the minutes until the window that blocks it ends. */
	public void acquireMessage(String tenantId, String userEmail, boolean demo) throws QorvaException {
		var limits = properties.getLimits();
		var now = Instant.now(clock);
		long hour = now.getEpochSecond() / 3600;
		long day = now.getEpochSecond() / 86_400;
		var userHour = "m:u:" + userEmail + ":h" + hour;
		var userDay = "m:u:" + userEmail + ":d" + day;
		var tenantDay = "m:t:" + tenantId + ":d" + day;
		int perUserPerDay = demo ? Math.min(limits.getDemoPerUserPerDay(), limits.getPerUserPerDay()) : limits.getPerUserPerDay();
		synchronized (this) {
			if (count(userHour) >= limits.getPerUserPerHour()) throw limited(QorvaErrorCodes.HELP_RATE_LIMITED, minutesLeft(now, 3600));
			if (count(userDay) >= perUserPerDay || count(tenantDay) >= limits.getPerTenantPerDay()) {
				throw limited(QorvaErrorCodes.HELP_RATE_LIMITED, minutesLeft(now, 86_400));
			}
			increment(userHour);
			increment(userDay);
			increment(tenantDay);
		}
	}

	public void acquireTicket(String userEmail) throws QorvaException {
		var now = Instant.now(clock);
		var key = "t:u:" + userEmail + ":d" + now.getEpochSecond() / 86_400;
		synchronized (this) {
			if (count(key) >= properties.getLimits().getTicketsPerUserPerDay()) {
				throw limited(QorvaErrorCodes.HELP_TICKET_RATE_LIMITED, minutesLeft(now, 86_400));
			}
			increment(key);
		}
	}

	private int count(String key) {
		var counter = counters.getIfPresent(key);
		return counter != null ? counter.get() : 0;
	}

	private void increment(String key) {
		counters.get(key, k -> new AtomicInteger()).incrementAndGet();
	}

	private static long minutesLeft(Instant now, long windowSeconds) {
		long left = windowSeconds - now.getEpochSecond() % windowSeconds;
		return Math.max(1, (left + 59) / 60);
	}

	private static QorvaException limited(String key, long minutes) {
		return new QorvaException(key, HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.TOO_MANY_REQUESTS, minutes);
	}
}
