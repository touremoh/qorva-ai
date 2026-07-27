package ai.qorva.core.enums;

/**
 * Write-time-computable quality defects stored denormalized on each CV (qualityFlags).
 * Negative flags exist for every completeness field so presence percentages can be
 * derived as {@code total - flagCount} from one indexed aggregation.
 * <p>
 * Deliberately NOT flags: freshness (decays with time — derived from contentDate range
 * counts), duplicates (cross-document), archived (separate boolean).
 */
public enum QualityFlagEnum {
	MISSING_EMAIL,
	MISSING_PHONE,
	MISSING_CONTACT,
	MISSING_NAME,
	MISSING_ROLE,
	NO_WORK_EXPERIENCE,
	NO_SKILLS,
	MISSING_CAREER_START_YEAR,
	MISSING_EDUCATION,
	MISSING_LANGUAGES,
	MISSING_CERTIFICATIONS,
	MISSING_SALARY,
	MISSING_LINKEDIN,
	MISSING_SUMMARY,
	NO_AI_ANALYSIS,
	LOW_AI_CONFIDENCE
}
