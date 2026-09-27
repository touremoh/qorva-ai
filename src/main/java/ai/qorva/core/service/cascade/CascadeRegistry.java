package ai.qorva.core.service.cascade;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one place that knows what depends on what. A resource delete calls {@link #parentsDeleted}
 * after removing its own documents; deleting a CV removes its reports, which in turn removes the
 * reports' notes and chats, which removes the chats' messages. Tenant-wide wipes go through
 * {@link #purgeTenant} so the clear-library and demo-purge lists can no longer drift apart.
 */
@Slf4j
@Component
public class CascadeRegistry {

	private final List<CascadeParticipant> participants;

	public CascadeRegistry(List<CascadeParticipant> participants) {
		this.participants = participants;
	}

	/** Cascades the deletion of {@code ids} of {@code type} in {@code tenantId}. Returns deleted counts by collection. */
	public Map<String, Long> parentsDeleted(CascadeResource type, String tenantId, Collection<String> ids) {
		var counts = new LinkedHashMap<String, Long>();
		if (ids == null || ids.isEmpty()) {
			return counts;
		}
		record Pending(CascadeResource type, Collection<String> ids) {}
		var queue = new ArrayDeque<Pending>();
		queue.add(new Pending(type, ids));
		while (!queue.isEmpty()) {
			var next = queue.poll();
			for (var participant : participants) {
				for (var deleted : participant.onParentsDeleted(next.type(), tenantId, next.ids())) {
					counts.merge(deleted.collection(), deleted.count(), Long::sum);
					if (deleted.resource() != null && !deleted.ids().isEmpty()) {
						queue.add(new Pending(deleted.resource(), deleted.ids()));
					}
				}
			}
		}
		log.info("Cascade after deleting {} {} for tenant {}: {}", ids.size(), type, tenantId, counts);
		return counts;
	}

	/**
	 * Deletes the tenant's data covered by {@code scope}. With {@code bestEffort}, a failing collection
	 * is logged and skipped so the rest still goes; otherwise the first failure propagates.
	 */
	public Map<String, Long> purgeTenant(String tenantId, PurgeScope scope, boolean bestEffort) {
		if (tenantId == null || tenantId.isBlank()) {
			throw new IllegalArgumentException("A tenant purge requires a tenant id");
		}
		var counts = new LinkedHashMap<String, Long>();
		for (var participant : participants) {
			try {
				participant.onTenantPurge(tenantId, scope).forEach((k, v) -> counts.merge(k, v, Long::sum));
			} catch (RuntimeException e) {
				if (!bestEffort) throw e;
				log.warn("Purge step {} failed for tenant {}", participant.getClass().getSimpleName(), tenantId, e);
			}
		}
		log.info("Purged tenant {} ({}): {}", tenantId, scope, counts);
		return counts;
	}
}
