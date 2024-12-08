package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.User;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.MongoTemplate;

@Repository
public class UserRepository extends AbstractQorvaRepository<User> {
    public UserRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, User.class);
    }
}
