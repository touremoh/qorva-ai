package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dao.entity.StripeEventLog;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.StripeEventLogSpecifications;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StripeEventLogQueryBuilder implements QorvaQueryBuilder<StripeEventLog> {

	@Override
	public MongoSpecification<StripeEventLog> buildQuery(Map<String, String> params) {
		return MongoSpecification
			.where(StripeEventLogSpecifications.tenantIdEquals(params.get("tenantId")))
			.and(StripeEventLogSpecifications.eventTypeEquals(params.get("eventType")))
			.and(StripeEventLogSpecifications.eventStatusEquals(params.get("eventStatus")))
			.and(StripeEventLogSpecifications.stripeCustomerIdEquals(params.get("stripeCustomerId")))
			.and(StripeEventLogSpecifications.stripeSubscriptionIdEquals(params.get("stripeSubscriptionId")));
	}
}
