package ai.qorva.core.qbe;

import ai.qorva.core.dao.entity.Client;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;

@Component
public class ClientQueryBuilder implements QorvaQueryBuilder<Client> {
	@Override
	public Example<Client> exampleOf(Client entity) {
		var matcher = ExampleMatcher.matchingAll()
			.withIgnoreNullValues()
			.withMatcher("id", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("tenantId", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("clientCode", ExampleMatcher.GenericPropertyMatchers.ignoreCase().exact())
			.withMatcher("name", ExampleMatcher.GenericPropertyMatchers.ignoreCase().exact());

		return Example.of(entity, matcher);
	}
}
