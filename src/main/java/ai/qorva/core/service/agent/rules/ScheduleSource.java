package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** A daily or weekly slot in the rule's own time zone. A missed slot (scheduler down) fires once, late. */
@Component
public class ScheduleSource implements AgentTriggerSource {

	@Override
	public String type() {
		return AgentRule.TRIGGER_SCHEDULE;
	}

	@Override
	public int perRun(int batchSize) {
		return 1;
	}

	@Override
	public List<RuleSubject> newSubjects(AgentRule rule, Instant since, Instant now, int limit) {
		var slot = rule.getNextRunAt();
		if (slot == null || slot.isAfter(now)) {
			return List.of();
		}
		return List.of(new RuleSubject("slot:" + slot, slot, List.of(), "scheduled run of " + slot));
	}

	/**
	 * First slot strictly after {@code after}. Computed on local date-time, so an hour skipped by a DST change
	 * moves to the next valid time, and a repeated hour fires once.
	 */
	public static Instant nextSlot(AgentRule.Trigger trigger, Instant after) {
		var zone = ZoneId.of(trigger.getZoneId());
		var local = ZonedDateTime.ofInstant(after, zone);
		var candidate = local.truncatedTo(ChronoUnit.DAYS).withHour(trigger.getHour());
		if (AgentRule.Trigger.WEEKLY.equals(trigger.getFrequency())) {
			candidate = candidate.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(trigger.getWeekday())));
			if (!candidate.toInstant().isAfter(after)) {
				candidate = candidate.toLocalDate().plusWeeks(1).atTime(trigger.getHour(), 0).atZone(zone);
			}
		} else if (!candidate.toInstant().isAfter(after)) {
			candidate = candidate.toLocalDate().plusDays(1).atTime(trigger.getHour(), 0).atZone(zone);
		}
		return candidate.toInstant();
	}
}
