package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.UserSpecifications;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UserQueryBuilder implements QorvaQueryBuilder<User> {

	@Override
	public MongoSpecification<User> buildQuery(Map<String, String> params) {
		return MongoSpecification
			.where(UserSpecifications.tenantIdEquals(params.get("tenantId")))
			.and(UserSpecifications.firstNameContains(params.get("firstName")))
			.and(UserSpecifications.lastNameContains(params.get("lastName")))
			.and(UserSpecifications.emailContains(params.get("email")))
			.and(UserSpecifications.userAccountStatusEquals(params.get("userAccountStatus")));
	}
}
