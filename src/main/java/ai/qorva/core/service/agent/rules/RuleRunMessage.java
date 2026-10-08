package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * What a rule run is asked to do: the goal template with its placeholders filled in, and the message the
 * model receives — the goal plus the records it concerns, ids included.
 */
public record RuleRunMessage(String goal, String message, List<AgentRun.Mention> mentions) {

	static final int MAX_MENTIONS = 50;

	public static RuleRunMessage of(AgentRule rule, List<RuleSubject> subjects) {
		var unique = new LinkedHashMap<String, AgentRun.Mention>();
		subjects.stream().flatMap(s -> s.mentions().stream())
			.forEach(m -> unique.putIfAbsent(m.getType() + ":" + m.getId(), m));
		var mentions = new ArrayList<>(unique.values()).subList(0, Math.min(unique.size(), MAX_MENTIONS));

		var goal = rule.getGoalTemplate()
			.replace("{{candidates}}", names(mentions, "CV"))
			.replace("{{job}}", names(mentions, "JOB"))
			.replace("{{count}}", String.valueOf(subjects.size()))
			.replace("{{sync}}", rule.getTrigger().getConnectionName() != null ? rule.getTrigger().getConnectionName() : "the ATS")
			.replace("{{rule}}", rule.getName());

		var message = new StringBuilder(goal).append("\n\n(Started by the standing rule \"").append(rule.getName())
			.append("\": ").append(describe(rule.getTrigger())).append('.');
		if (!subjects.isEmpty() && !AgentRule.TRIGGER_SCHEDULE.equals(rule.getTrigger().getType())) {
			message.append(" Records it concerns:");
			subjects.forEach(s -> message.append("\n- ").append(s.line()));
		}
		message.append(')');
		return new RuleRunMessage(goal, message.toString(), List.copyOf(mentions));
	}

	private static String names(List<AgentRun.Mention> mentions, String type) {
		var names = mentions.stream().filter(m -> type.equals(m.getType())).map(AgentRun.Mention::getName).distinct()
			.collect(Collectors.joining(", "));
		return names.isEmpty() ? (type.equals("CV") ? "the candidates listed below" : "the job") : names;
	}

	/** A stale reason as the model reads it. */
	static String reasonText(String reason) {
		return switch (reason) {
			case "NEVER_RUN" -> "never matched";
			case "JOB_CHANGED" -> "the job changed";
			case "NEW_CANDIDATES" -> "new candidates would rank in its top results";
			case "CANDIDATE_CHANGED" -> "candidates in its results changed";
			default -> "its results are out of date";
		};
	}

	/** One line on what fired, for the model. */
	static String describe(AgentRule.Trigger trigger) {
		return switch (trigger.getType()) {
			case AgentRule.TRIGGER_CV_ADDED -> "new candidates were added to the library"
				+ (AgentRule.Trigger.SOURCE_ATS.equals(trigger.getSource()) ? " from the ATS"
				: AgentRule.Trigger.SOURCE_MANUAL.equals(trigger.getSource()) ? " by upload" : "");
			case AgentRule.TRIGGER_CV_SCORED -> "candidates were scored"
				+ (trigger.getJobTitle() != null ? " on the job " + trigger.getJobTitle() : "")
				+ scoreRange(trigger.getMinScore(), trigger.getMaxScore())
				+ (trigger.getRecommendations() != null ? " with the recommendation " + String.join(" or ", trigger.getRecommendations())
				: Boolean.TRUE.equals(trigger.getRecommendedOnly()) ? " and recommended for an interview" : "");
			case AgentRule.TRIGGER_ATS_SYNC_FINISHED -> "an ATS import finished";
			case AgentRule.TRIGGER_SCHEDULE -> (AgentRule.Trigger.WEEKLY.equals(trigger.getFrequency()) ? "weekly" : "daily")
				+ " schedule at " + trigger.getHour() + ":00 (" + trigger.getZoneId() + ")";
			case AgentRule.TRIGGER_JOB_NEEDS_MATCHING -> (trigger.getJobTitle() != null ? "the job " + trigger.getJobTitle() : "a job")
				+ " needs matching" + (trigger.getStaleReasons() != null
					? " (" + String.join(", ", trigger.getStaleReasons().stream().map(RuleRunMessage::reasonText).toList()) + ")" : "");
			case AgentRule.TRIGGER_REPORT_STATUS_CHANGED -> "a candidate's status changed"
				+ (trigger.getToStatuses() != null ? " to " + String.join(" or ", trigger.getToStatuses()) : "")
				+ (trigger.getJobTitle() != null ? " on the job " + trigger.getJobTitle() : "");
			case AgentRule.TRIGGER_REPORT_STATUS_IDLE -> "candidates stayed in " + String.join(" or ", nullToEmpty(trigger.getToStatuses()))
				+ " for " + trigger.getIdleDays() + " days without a status change"
				+ (trigger.getJobTitle() != null ? " on the job " + trigger.getJobTitle() : "");
			case AgentRule.TRIGGER_CV_OUTDATED -> "candidates' CVs became " + trigger.getStaleMonths() + " months old (outdated)"
				+ sourceText(trigger.getSource());
			case AgentRule.TRIGGER_JOB_CLOSED -> (trigger.getJobTitle() != null ? "the job " + trigger.getJobTitle() : "a job")
				+ " was closed";
			case AgentRule.TRIGGER_DUPLICATE_FOUND -> "new candidates have the same email or phone as an existing candidate"
				+ sourceText(trigger.getSource());
			case AgentRule.TRIGGER_CANDIDATE_PROFILE_UPDATED -> "candidates updated their own profile from a profile-update request";
			default -> trigger.getType();
		};
	}

	private static String scoreRange(Integer min, Integer max) {
		if (min != null && max != null) return " between " + min + " and " + max;
		if (min != null) return " at " + min + " or more";
		if (max != null) return " at " + max + " or less";
		return "";
	}

	private static String sourceText(String source) {
		return AgentRule.Trigger.SOURCE_ATS.equals(source) ? " (imported from the ATS)"
			: AgentRule.Trigger.SOURCE_MANUAL.equals(source) ? " (uploaded)" : "";
	}

	private static List<String> nullToEmpty(List<String> values) {
		return values != null ? values : List.of();
	}
}
