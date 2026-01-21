package ai.qorva.core.qbe;

import ai.qorva.core.dao.entity.ClientReport;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;

@Component
public class ClientReportQueryBuilder implements QorvaQueryBuilder<ClientReport> {
	@Override
	public Example<ClientReport> exampleOf(ClientReport entity) {
		var matcher = ExampleMatcher.matchingAll()
			.withIgnoreNullValues()
			.withMatcher("id", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("tenantId", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("clientId", ExampleMatcher.GenericPropertyMatchers.exact());

		return Example.of(entity, matcher);
	}
}
