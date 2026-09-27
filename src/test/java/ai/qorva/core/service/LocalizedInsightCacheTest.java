package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalizedInsightCacheTest {

	private final LocalizedInsightCache<String> cache = new LocalizedInsightCache<>(Duration.ofHours(1), 100);
	private final AtomicInteger generations = new AtomicInteger();
	private final AtomicInteger translations = new AtomicInteger();

	private String get(String fingerprint, String language) throws Exception {
		return cache.get("t1", fingerprint, language,
			() -> "summary#" + generations.incrementAndGet(),
			(canonical, lang) -> {
				translations.incrementAndGet();
				return lang + ":" + canonical;
			});
	}

	@Test
	void sameFingerprint_generatesOnce_andTranslatesOncePerLanguage() throws Exception {
		assertThat(get("a", "en")).isEqualTo("summary#1");
		assertThat(get("a", "fr")).isEqualTo("fr:summary#1");
		assertThat(get("a", "fr")).isEqualTo("fr:summary#1");

		assertThat(generations).hasValue(1);
		assertThat(translations).hasValue(1);
	}

	@Test
	void newFingerprint_regenerates_andDropsTranslations() throws Exception {
		get("a", "fr");
		assertThat(get("b", "fr")).isEqualTo("fr:summary#2");
		assertThat(translations).hasValue(2);
	}

	@Test
	void failedTranslation_returnsEnglish_andIsRetried() throws Exception {
		var attempts = new AtomicInteger();
		LocalizedInsightCache.Translator<String> failing = (canonical, lang) -> {
			attempts.incrementAndGet();
			throw QorvaErrors.of(QorvaErrorCodes.USAGE_INSIGHT_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
		};
		assertThat(cache.get("t1", "a", "de", () -> "english", failing)).isEqualTo("english");
		assertThat(cache.get("t1", "a", "de", () -> "english", failing)).isEqualTo("english");
		assertThat(attempts).hasValue(2);
	}

	@Test
	void failedGeneration_isThrown_andNotCached() {
		LocalizedInsightCache.Generator<String> failing = () -> {
			throw QorvaErrors.of(QorvaErrorCodes.USAGE_INSIGHT_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
		};
		assertThatThrownBy(() -> cache.get("t1", "a", "en", failing, (c, l) -> c))
			.hasMessage(QorvaErrorCodes.USAGE_INSIGHT_UNAVAILABLE);
		assertThat(generations).hasValue(0);
	}

	@Test
	void normalizeLanguage_keepsSupportedPrimaryTags() {
		assertThat(LocalizedInsightCache.normalizeLanguage("fr-FR,fr;q=0.9")).isEqualTo("fr");
		assertThat(LocalizedInsightCache.normalizeLanguage("NL")).isEqualTo("nl");
		assertThat(LocalizedInsightCache.normalizeLanguage("ja")).isEqualTo("en");
		assertThat(LocalizedInsightCache.normalizeLanguage(null)).isEqualTo("en");
	}
}
