package ai.qorva.core.service;

import org.springframework.data.mongodb.core.query.Criteria;

import java.util.HashSet;
import java.util.Set;

/** {@link TenantAccess} for unit tests: every tenant usable, unless listed in {@link #unusable}. */
public class TenantAccessStub extends TenantAccess {

	public final Set<String> unusable = new HashSet<>();

	public TenantAccessStub() {
		super(null);
	}

	@Override
	public Set<String> unusableTenantIds() {
		return Set.copyOf(unusable);
	}

	@Override
	public boolean isUsable(String tenantId) {
		return tenantId == null || !unusable.contains(tenantId);
	}

	@Override
	public Criteria usableTenantsOnly(String field) {
		return Criteria.where(field).nin(unusable.stream().map(org.bson.types.ObjectId::new).toList());
	}

	@Override
	public void evict() {
	}
}
