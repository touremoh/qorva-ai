package ai.qorva.core.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

	public static final String LIBRARY_QUALITY_CACHE = "libraryQuality";

	/**
	 * Per-tenant Library Quality report cache. Quality changes slowly, so a short TTL is
	 * invisible to passive viewers, while explicit eviction on every mutating action keeps
	 * the "score moves after I fix something" feedback loop honest. Entry ceiling sized for
	 * the tenant count, entries are a few KB each.
	 */
	@Bean
	public CacheManager cacheManager() {
		var cacheManager = new CaffeineCacheManager(LIBRARY_QUALITY_CACHE);
		cacheManager.setCaffeine(Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(5))
			.maximumSize(10_000));
		return cacheManager;
	}
}
