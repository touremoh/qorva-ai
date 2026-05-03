package ai.qorva.core.dao.specifications;

import ai.qorva.core.dao.entity.StripeEventLog;
import org.springframework.data.mongodb.core.query.Criteria;

public final class StripeEventLogSpecifications {
	private StripeEventLogSpecifications() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static MongoSpecification<StripeEventLog> tenantIdEquals(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("tenantId").is(tenantId);
	}

	public static MongoSpecification<StripeEventLog> eventTypeEquals(String eventType) {
		if (eventType == null || eventType.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("eventType").is(eventType);
	}

	public static MongoSpecification<StripeEventLog> eventStatusEquals(String eventStatus) {
		if (eventStatus == null || eventStatus.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("eventStatus").is(eventStatus);
	}

	public static MongoSpecification<StripeEventLog> stripeCustomerIdEquals(String stripeCustomerId) {
		if (stripeCustomerId == null || stripeCustomerId.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("stripeCustomerId").is(stripeCustomerId);
	}

	public static MongoSpecification<StripeEventLog> stripeSubscriptionIdEquals(String stripeSubscriptionId) {
		if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("stripeSubscriptionId").is(stripeSubscriptionId);
	}
}
