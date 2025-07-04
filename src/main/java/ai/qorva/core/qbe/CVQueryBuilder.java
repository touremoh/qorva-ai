package ai.qorva.core.qbe;

import ai.qorva.core.dao.entity.CV;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Component;

@Component
public class CVQueryBuilder implements QorvaQueryBuilder<CV> {

	@Override
	public Example<CV> exampleOf(CV entity) {
		var matcher = ExampleMatcher.matchingAll()
			.withIgnoreNullValues()
			.withMatcher("id", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("tenantId", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("candidateProfileSummary", ExampleMatcher.GenericPropertyMatchers.ignoreCase().contains())
			.withMatcher("nbYearsOfExperience", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("personalInformation.name", ExampleMatcher.GenericPropertyMatchers.exact())
			.withMatcher("personalInformation.role", ExampleMatcher.GenericPropertyMatchers.ignoreCase().contains())
			.withMatcher("keySkills", ExampleMatcher.GenericPropertyMatchers.ignoreCase().contains())
			.withMatcher("tags", ExampleMatcher.GenericPropertyMatchers.ignoreCase().contains());

		return Example.of(entity, matcher);
	}
}
