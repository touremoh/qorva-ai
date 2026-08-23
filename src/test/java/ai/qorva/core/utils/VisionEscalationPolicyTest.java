package ai.qorva.core.utils;

import ai.qorva.core.enums.QualityFlagEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisionEscalationPolicyTest {

	private static final String LONG_TEXT = "x".repeat(VisionEscalationPolicy.MIN_TEXT_CHARS + 50);

	@Test
	void thinOrMissingTextIsTooThin() {
		assertThat(VisionEscalationPolicy.isTextTooThin(null)).isTrue();
		assertThat(VisionEscalationPolicy.isTextTooThin("   ")).isTrue();
		assertThat(VisionEscalationPolicy.isTextTooThin("short header only")).isTrue();
		assertThat(VisionEscalationPolicy.isTextTooThin(LONG_TEXT)).isFalse();
	}

	@Test
	void escalatesOnThinTextRegardlessOfFlags() {
		assertThat(VisionEscalationPolicy.shouldEscalate("scan", List.of())).isTrue();
	}

	@Test
	void escalatesOnPixelHiddenContentSymptoms() {
		assertThat(VisionEscalationPolicy.shouldEscalate(LONG_TEXT,
			List.of(QualityFlagEnum.MISSING_CONTACT.name()))).isTrue();
		assertThat(VisionEscalationPolicy.shouldEscalate(LONG_TEXT,
			List.of(QualityFlagEnum.LOW_AI_CONFIDENCE.name()))).isTrue();
		assertThat(VisionEscalationPolicy.shouldEscalate(LONG_TEXT,
			List.of(QualityFlagEnum.NO_AI_ANALYSIS.name()))).isTrue();
	}

	@Test
	void healthyExtractionDoesNotEscalate() {
		assertThat(VisionEscalationPolicy.shouldEscalate(LONG_TEXT, List.of())).isFalse();
		assertThat(VisionEscalationPolicy.shouldEscalate(LONG_TEXT, null)).isFalse();
		assertThat(VisionEscalationPolicy.shouldEscalate(LONG_TEXT,
			List.of(QualityFlagEnum.NO_WORK_EXPERIENCE.name()))).isFalse();
	}
}
