package ai.qorva.core.service.cascade;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Owns the documents of one collection (or a pair that live and die together) that depend on other
 * resources. Every query a participant runs is scoped by the tenant it is given.
 */
public interface CascadeParticipant {

	/** What a participant deleted, and — when those were parents too — which resource and ids. */
	record Deleted(String collection, long count, CascadeResource resource, List<String> ids) {
		public static Deleted of(String collection, long count) {
			return new Deleted(collection, count, null, List.of());
		}
	}

	/** Deletes this participant's documents that depend on the given, just-deleted parents. */
	default List<Deleted> onParentsDeleted(CascadeResource parent, String tenantId, Collection<String> parentIds) {
		return List.of();
	}

	/** Deletes this participant's documents of the tenant when the scope covers them; counts by collection. */
	default Map<String, Long> onTenantPurge(String tenantId, PurgeScope scope) {
		return Map.of();
	}
}
