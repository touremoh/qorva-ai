package ai.qorva.core.qbe;

import ai.qorva.core.dao.entity.StripeEventLog;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;

@Component
public class StripeEventLogQueryBuilder implements QorvaQueryBuilder<StripeEventLog> {
	@Override
	public Example<StripeEventLog> exampleOf(StripeEventLog entity) {
		var matcher = ExampleMatcher.matchingAll()
			.withIgnoreNullValues()
			.withMatcher("id", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("tenantId", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("eventType", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("stripeCustomerId", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("stripeSubscriptionId", ExampleMatcher.GenericPropertyMatchers.exact())
			;

		return Example.of(entity, matcher);
	}
}
