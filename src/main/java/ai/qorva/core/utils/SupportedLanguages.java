package ai.qorva.core.utils;

import java.util.Locale;
import java.util.Map;

/**
 * The seven UI languages, as the app sends them in Accept-Language. Anything else falls back to English.
 */
public final class SupportedLanguages {

	private static final Map<String, String> NAMES = Map.of(
		"en", "English", "fr", "French", "de", "German", "es", "Spanish", "it", "Italian", "nl", "Dutch", "pt", "Portuguese");

	private SupportedLanguages() {
	}

	/** {@code fr-BE}, {@code fr_BE}, {@code fr,en;q=0.8} → {@code fr}; unknown or blank → {@code en}. */
	public static String normalize(String code) {
		if (code == null || code.isBlank()) return "en";
		var primary = code.split("[-_,;]")[0].trim().toLowerCase(Locale.ROOT);
		return NAMES.containsKey(primary) ? primary : "en";
	}

	/** The English name of the language, for a prompt ("Answer in French"). */
	public static String name(String code) {
		return NAMES.get(normalize(code));
	}
}
