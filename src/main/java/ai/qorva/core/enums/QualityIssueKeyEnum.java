package ai.qorva.core.enums;

/**
 * Actionable findings surfaced by the Library Quality report. Each key (except DUPLICATES,
 * which routes to the existing duplicates feature) maps to a drill-down CV list.
 */
public enum QualityIssueKeyEnum {
	MISSING_CONTACT,
	MISSING_EMAIL,
	MISSING_PHONE,
	NO_WORK_EXPERIENCE,
	NO_SKILLS,
	MISSING_SUMMARY,
	OUTDATED,
	UNKNOWN_FRESHNESS,
	LOW_PARSE_CONFIDENCE,
	DUPLICATES
}
