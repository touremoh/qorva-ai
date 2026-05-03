package ai.qorva.core.dao.specifications;

import ai.qorva.core.dao.entity.Tenant;
import org.springframework.data.mongodb.core.query.Criteria;

public final class TenantSpecifications {
	private TenantSpecifications() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static MongoSpecification<Tenant> tenantNameContains(String tenantName) {
		if (tenantName == null || tenantName.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("tenantName").regex(tenantName, "i");
	}

	public static MongoSpecification<Tenant> stripeCustomerIdEquals(String stripeCustomerId) {
		if (stripeCustomerId == null || stripeCustomerId.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("stripeCustomerId").is(stripeCustomerId);
	}
}
