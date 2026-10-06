package ai.qorva.core.service.help;

import ai.qorva.core.config.HelpProperties;
import ai.qorva.core.exception.QorvaException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HelpRateLimiterTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:30:00Z"), ZoneOffset.UTC);

	private static HelpProperties properties() {
		var p = new HelpProperties();
		p.getLimits().setPerUserPerHour(3);
		p.getLimits().setPerUserPerDay(5);
		p.getLimits().setPerTenantPerDay(4);
		p.getLimits().setDemoPerUserPerDay(2);
		p.getLimits().setTicketsPerUserPerDay(1);
		return p;
	}

	@Test
	void theHourlyLimitRefusesWithTheMinutesLeft() throws Exception {
		var limiter = new HelpRateLimiter(properties(), CLOCK);
		for (int i = 0; i < 3; i++) limiter.acquireMessage("t1", "a@x.test", false);
		assertThatThrownBy(() -> limiter.acquireMessage("t1", "a@x.test", false))
			.isInstanceOf(QorvaException.class)
			.satisfies(e -> {
				var qe = (QorvaException) e;
				assertThat(qe.getHttpStatusCode()).isEqualTo(429);
				assertThat(qe.getParams()).containsExactly(30L);
			});
		// Another user of the same tenant is not affected by a's hourly limit.
		limiter.acquireMessage("t1", "b@x.test", false);
	}

	@Test
	void theTenantDailyLimitCoversAllItsUsers() throws Exception {
		var limiter = new HelpRateLimiter(properties(), CLOCK);
		limiter.acquireMessage("t1", "a@x.test", false);
		limiter.acquireMessage("t1", "b@x.test", false);
		limiter.acquireMessage("t1", "c@x.test", false);
		limiter.acquireMessage("t1", "d@x.test", false);
		assertThatThrownBy(() -> limiter.acquireMessage("t1", "e@x.test", false)).isInstanceOf(QorvaException.class);
		limiter.acquireMessage("t2", "e@x.test", false);
	}

	@Test
	void demoUsersHaveTheTighterDailyCap() throws Exception {
		var limiter = new HelpRateLimiter(properties(), CLOCK);
		limiter.acquireMessage("t1", "demo@x.test", true);
		limiter.acquireMessage("t1", "demo@x.test", true);
		assertThatThrownBy(() -> limiter.acquireMessage("t1", "demo@x.test", true)).isInstanceOf(QorvaException.class);
	}

	@Test
	void aRefusedQuestionIsNotCounted() throws Exception {
		var limiter = new HelpRateLimiter(properties(), CLOCK);
		for (int i = 0; i < 3; i++) limiter.acquireMessage("t1", "a@x.test", false);
		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> limiter.acquireMessage("t1", "a@x.test", false)).isInstanceOf(QorvaException.class);
		}
		// The tenant still has its fourth question: refusals did not use it up.
		limiter.acquireMessage("t1", "b@x.test", false);
	}

	@Test
	void ticketsHaveTheirOwnDailyLimit() throws Exception {
		var limiter = new HelpRateLimiter(properties(), CLOCK);
		limiter.acquireTicket("a@x.test");
		assertThatThrownBy(() -> limiter.acquireTicket("a@x.test")).isInstanceOf(QorvaException.class);
		limiter.acquireMessage("t1", "a@x.test", false);
	}
}
