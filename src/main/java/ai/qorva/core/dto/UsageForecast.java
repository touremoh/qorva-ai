package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * Where one meter is heading at the current pace — computed per request, never stored.
 *
 * @param projectedConsumed units used by the end of the period at the current pace; null when too early or unmetered
 * @param limitReachedOn    when the limit is crossed at that pace; set only for {@link Status#WILL_EXCEED}
 */
public record UsageForecast(
	Long projectedConsumed,
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
	Instant limitReachedOn,
	Status status
) {

	public enum Status {
		/** Not enough of the period has elapsed for the pace to mean anything. */
		TOO_EARLY,
		/** Projected at or under 80 % of the limit. */
		ON_TRACK,
		/** Projected between 80 % and 100 % of the limit. */
		WATCH,
		/** Projected past the limit before the period ends. */
		WILL_EXCEED,
		/** The limit is already used up. */
		REACHED,
		/** The period has no limit for this meter. */
		UNMETERED
	}
}
