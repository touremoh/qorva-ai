package ai.qorva.core.qbe;

import ai.qorva.core.dao.entity.DemoRequestor;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;

@Component
public class DemoRequestorQueryBuilder implements QorvaQueryBuilder<DemoRequestor> {
	@Override
	public Example<DemoRequestor> exampleOf(DemoRequestor entity) {
		var matcher = ExampleMatcher.matchingAll()
			.withIgnoreNullValues()
			.withMatcher("id", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("tenantId", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("email", ExampleMatcher.GenericPropertyMatchers.ignoreCase().exact())
			.withMatcher("firstName", ExampleMatcher.GenericPropertyMatchers.ignoreCase().exact())
			.withMatcher("lastName", ExampleMatcher.GenericPropertyMatchers.ignoreCase().exact());

		return Example.of(entity, matcher);
	}
}
