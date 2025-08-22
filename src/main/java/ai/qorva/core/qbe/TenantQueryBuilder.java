package ai.qorva.core.qbe;

import ai.qorva.core.dao.entity.Tenant;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;

@Component
public class TenantQueryBuilder implements QorvaQueryBuilder<Tenant> {
	@Override
	public Example<Tenant> exampleOf(Tenant entity) {
		var matcher = ExampleMatcher.matchingAll()
			.withIgnoreNullValues()
			.withMatcher("id", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("tenantName", ExampleMatcher.GenericPropertyMatchers.ignoreCase().exact())
			.withMatcher("stripeCustomerId", ExampleMatcher.GenericPropertyMatchers.exact())
			;

		return Example.of(entity, matcher);
	}
}
