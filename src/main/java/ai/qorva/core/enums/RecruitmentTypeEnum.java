package ai.qorva.core.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * Recruitment focus a demo tenant self-selects at signup ("What type of recruitment do you mostly do?").
 * Drives which sample-data fixture set is seeded from S3.
 * <p>
 * {@code slug} is the S3 folder name for this segment (see DemoSeedService / S3 layout
 * {@code demo-seed/<version>/<slug>/<lang>/...}).
 */
@Getter
public enum RecruitmentTypeEnum {
	TECH_IT("TECH_IT", "tech-it"),
	ENGINEERING("ENGINEERING", "engineering"),
	FINANCE_ACCOUNTING("FINANCE_ACCOUNTING", "finance-accounting"),
	SALES_MARKETING("SALES_MARKETING", "sales-marketing"),
	HEALTHCARE_LIFESCIENCES("HEALTHCARE_LIFESCIENCES", "healthcare-life-sciences"),
	EXECUTIVE_MANAGEMENT("EXECUTIVE_MANAGEMENT", "executive-management"),
	GENERALIST("GENERALIST", "generalist");

	RecruitmentTypeEnum(String value, String slug) {
		this.value = value;
		this.slug = slug;
	}

	private final String value;

	/** S3 folder name for this segment's fixtures. */
	private final String slug;

	/** Returns the segment whose value matches (case-insensitive), or empty if unknown. */
	public static Optional<RecruitmentTypeEnum> fromValue(String value) {
		if (value == null) {
			return Optional.empty();
		}
		return Arrays.stream(values())
			.filter(t -> t.value.equalsIgnoreCase(value))
			.findFirst();
	}
}
