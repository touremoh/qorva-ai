package ai.qorva.core.qbe;

import ai.qorva.core.dao.entity.InterviewQuestions;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;

@Component
public class InterviewQuestionsQueryBuilder implements QorvaQueryBuilder<InterviewQuestions> {
	@Override
	public Example<InterviewQuestions> exampleOf(InterviewQuestions entity) {
		var matcher = ExampleMatcher.matchingAll()
			.withIgnoreNullValues()
			.withMatcher("id", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("tenantId", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("jobPostId", ExampleMatcher.GenericPropertyMatchers.exact());

		return Example.of(entity, matcher);
	}
}
