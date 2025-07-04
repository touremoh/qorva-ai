package ai.qorva.core.qbe;

import ai.qorva.core.dao.entity.JobPost;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;

@Component
public class JobPostQueryBuilder implements QorvaQueryBuilder<JobPost> {
	@Override
	public Example<JobPost> exampleOf(JobPost entity) {
		var matcher = ExampleMatcher.matchingAll()
			.withIgnoreNullValues()
			.withMatcher("id", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("tenantId", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("status", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("title", ExampleMatcher.GenericPropertyMatchers.ignoreCase().contains())
			.withMatcher("description", ExampleMatcher.GenericPropertyMatchers.ignoreCase().contains())
			;

		return Example.of(entity, matcher);
	}
}
