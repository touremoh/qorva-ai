package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.User;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends QorvaRepository<User> {
	User findByEmail(String email);

	@Query("{ 'tenantId': ?0 }")
	@Update(update = "{ '$set': { 'userAccountStatus': ?1 } }")
	long updateUserAccountStatusByTenantId(String tenantId, String status);
}
