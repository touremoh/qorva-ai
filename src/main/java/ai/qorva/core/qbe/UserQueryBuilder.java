package ai.qorva.core.qbe;

import ai.qorva.core.dao.entity.User;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;

@Component
public class UserQueryBuilder implements QorvaQueryBuilder<User> {
	@Override
	public Example<User> exampleOf(User entity) {
		var matcher = ExampleMatcher.matching()
			.withIgnoreNullValues()
			.withMatcher("id", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("tenantId", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("firstName", ExampleMatcher.GenericPropertyMatchers.ignoreCase().exact())
			.withMatcher("lastName", ExampleMatcher.GenericPropertyMatchers.ignoreCase().exact())
			.withMatcher("email", ExampleMatcher.GenericPropertyMatchers.ignoreCase().exact())
			;

		return Example.of(entity, matcher);
	}
}
