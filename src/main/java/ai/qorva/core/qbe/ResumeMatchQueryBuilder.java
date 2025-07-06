package ai.qorva.core.qbe;

import ai.qorva.core.dao.entity.ResumeMatch;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;

@Component
public class ResumeMatchQueryBuilder implements QorvaQueryBuilder<ResumeMatch> {
	@Override
	public Example<ResumeMatch> exampleOf(ResumeMatch entity) {
		var matcher = ExampleMatcher.matchingAll()
			.withIgnoreNullValues()
			.withMatcher("id", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("tenantId", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("jobPostId", ExampleMatcher.GenericPropertyMatchers.exact());

		return Example.of(entity, matcher);
	}
}
