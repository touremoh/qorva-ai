package ai.qorva.core.utils;

import ai.qorva.core.enums.QualityFlagEnum;
import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Set;

/**
 * Decides when a CV should be re-extracted with a vision model. Text-only extraction is
 * blind to information rendered as pixels (designed headers, scanned pages, photos with
 * contact details), so these cheap, deterministic signals route the affected minority of
 * CVs through the image path.
 */
@UtilityClass
public class VisionEscalationPolicy {

	/** Below this, the text layer is almost certainly not the whole document (scanned/designed CV). */
	public static final int MIN_TEXT_CHARS = 300;

	private static final Set<String> TRIGGER_FLAGS = Set.of(
		QualityFlagEnum.MISSING_CONTACT.name(),
		QualityFlagEnum.LOW_AI_CONFIDENCE.name(),
		QualityFlagEnum.NO_AI_ANALYSIS.name()
	);

	/** True when the text layer alone is too thin to bother with a text-first pass. */
	public boolean isTextTooThin(String rawText) {
		return rawText == null || rawText.strip().length() < MIN_TEXT_CHARS;
	}

	/** True when a completed text-first extraction shows symptoms of pixel-hidden content. */
	public boolean shouldEscalate(String rawText, Collection<String> qualityFlags) {
		if (isTextTooThin(rawText)) {
			return true;
		}
		return qualityFlags != null && qualityFlags.stream().anyMatch(TRIGGER_FLAGS::contains);
	}
}
