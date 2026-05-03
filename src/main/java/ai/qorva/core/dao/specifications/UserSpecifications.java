package ai.qorva.core.dao.specifications;

import ai.qorva.core.dao.entity.User;
import org.springframework.data.mongodb.core.query.Criteria;

public final class UserSpecifications {
	private UserSpecifications() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static MongoSpecification<User> tenantIdEquals(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("tenantId").is(tenantId);
	}

	public static MongoSpecification<User> firstNameContains(String firstName) {
		if (firstName == null || firstName.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("firstName").regex(firstName, "i");
	}

	public static MongoSpecification<User> lastNameContains(String lastName) {
		if (lastName == null || lastName.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("lastName").regex(lastName, "i");
	}

	public static MongoSpecification<User> emailContains(String email) {
		if (email == null || email.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("email").regex(email, "i");
	}

	public static MongoSpecification<User> userAccountStatusEquals(String status) {
		if (status == null || status.isBlank()) return MongoSpecifications.empty();
		return () -> Criteria.where("userAccountStatus").is(status);
	}
}
