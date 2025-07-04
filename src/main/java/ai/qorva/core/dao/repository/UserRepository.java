package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.User;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends QorvaRepository<User> {
	User findByEmail(String email);
}
