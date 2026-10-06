package ai.qorva.core.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedLanguagesTest {

	@Test
	void normalizesHeaderValuesToTheSevenLanguages() {
		assertThat(SupportedLanguages.normalize("fr-BE")).isEqualTo("fr");
		assertThat(SupportedLanguages.normalize("pt_BR")).isEqualTo("pt");
		assertThat(SupportedLanguages.normalize("de,en;q=0.8")).isEqualTo("de");
		assertThat(SupportedLanguages.normalize(" NL ")).isEqualTo("nl");
		assertThat(SupportedLanguages.normalize("ja")).isEqualTo("en");
		assertThat(SupportedLanguages.normalize(null)).isEqualTo("en");
		assertThat(SupportedLanguages.normalize("")).isEqualTo("en");
	}

	@Test
	void namesTheLanguageForAPrompt() {
		assertThat(SupportedLanguages.name("es-MX")).isEqualTo("Spanish");
		assertThat(SupportedLanguages.name("it")).isEqualTo("Italian");
		assertThat(SupportedLanguages.name("xx")).isEqualTo("English");
	}
}
