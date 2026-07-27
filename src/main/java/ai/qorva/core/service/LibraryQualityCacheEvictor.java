package ai.qorva.core.service;

import ai.qorva.core.config.CacheConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * Single choke point for invalidating a tenant's Library Quality report cache.
 * Rule: every mutation path that can change quality (CV create/update/delete, bulk
 * actions, duplicate resolution, jobs) calls {@link #evict}; every frontend action
 * refetches afterwards — that pairing keeps the "score moves after I fix something"
 * feedback loop honest despite the TTL cache.
 */
@Slf4j
@Component
public class LibraryQualityCacheEvictor {

	@CacheEvict(cacheNames = CacheConfig.LIBRARY_QUALITY_CACHE, key = "#tenantId")
	public void evict(String tenantId) {
		log.debug("Library quality cache evicted for tenant={}", tenantId);
	}
}
