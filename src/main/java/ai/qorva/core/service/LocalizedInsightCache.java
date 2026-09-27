package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The per-tenant store behind every AI summary: one English summary per state ("fingerprint"),
 * translated on demand per language.
 * <ul>
 *   <li>Nothing is regenerated while the fingerprint is unchanged.</li>
 *   <li>Concurrent callers on a stale entry wait for one generation.</li>
 *   <li>A new fingerprint drops every translation at once.</li>
 *   <li>A failed translation falls back to English (not cached, so the next view retries).</li>
 * </ul>
 * Per instance: a miss costs one model call.
 *
 * @param <C> the summary type
 */
public final class LocalizedInsightCache<C> {

	public static final String DEFAULT_LANGUAGE = "en";
	/** The app's locales ({@code qorva-ai-app/src/i18n.js}); anything else is answered in English. */
	public static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "fr", "de", "es", "pt", "it", "nl");

	@FunctionalInterface
	public interface Generator<C> {
		C generate() throws QorvaException;
	}

	@FunctionalInterface
	public interface Translator<C> {
		C translate(C canonical, String language) throws QorvaException;
	}

	private record Entry<C>(String fingerprint, C canonical, Map<String, C> translations) {}

	/** Carries a checked failure out of a cache loader. */
	private static final class LoadFailure extends RuntimeException {
		private final QorvaException failure;

		LoadFailure(QorvaException failure) {
			super(failure);
			this.failure = failure;
		}
	}

	private final Cache<String, Entry<C>> cache;

	public LocalizedInsightCache(Duration ttl, long maximumSize) {
		this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maximumSize).build();
	}

	/**
	 * @param language already normalised ({@link #normalizeLanguage})
	 */
	public C get(String tenantId, String fingerprint, String language,
	             Generator<C> generator, Translator<C> translator) throws QorvaException {
		Entry<C> entry;
		try {
			entry = cache.asMap().compute(tenantId, (key, existing) ->
				existing != null && existing.fingerprint().equals(fingerprint) ? existing : generate(fingerprint, generator));
		} catch (LoadFailure e) {
			throw e.failure;
		}
		if (DEFAULT_LANGUAGE.equals(language)) {
			return entry.canonical();
		}
		try {
			return entry.translations().computeIfAbsent(language, lang -> translate(entry.canonical(), lang, translator));
		} catch (LoadFailure e) {
			return entry.canonical();
		}
	}

	private Entry<C> generate(String fingerprint, Generator<C> generator) {
		try {
			return new Entry<>(fingerprint, generator.generate(), new ConcurrentHashMap<>());
		} catch (QorvaException e) {
			throw new LoadFailure(e);
		}
	}

	private C translate(C canonical, String language, Translator<C> translator) {
		try {
			return translator.translate(canonical, language);
		} catch (QorvaException e) {
			throw new LoadFailure(e);
		}
	}

	/** {@code fr-FR,fr;q=0.9} → {@code fr}; unsupported or missing → {@code en}. */
	public static String normalizeLanguage(String acceptLanguage) {
		if (acceptLanguage == null || acceptLanguage.isBlank()) {
			return DEFAULT_LANGUAGE;
		}
		var first = acceptLanguage.split(",")[0].split(";")[0].trim();
		var code = Locale.forLanguageTag(first).getLanguage().toLowerCase(Locale.ROOT);
		return SUPPORTED_LANGUAGES.contains(code) ? code : DEFAULT_LANGUAGE;
	}
}
