package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.TenantSpecifications;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TenantQueryBuilder implements QorvaQueryBuilder<Tenant> {

	@Override
	public MongoSpecification<Tenant> buildQuery(Map<String, String> params) {
		return MongoSpecification
			.where(TenantSpecifications.tenantNameContains(params.get("tenantName")))
			.and(TenantSpecifications.stripeCustomerIdEquals(params.get("stripeCustomerId")))
			.and(TenantSpecifications.organizationIdEquals(params.get("organizationId")));
	}
}
