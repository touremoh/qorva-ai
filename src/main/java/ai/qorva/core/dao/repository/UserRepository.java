package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.User;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Repository
public class UserRepository extends AbstractQorvaRepository<User> {
    public UserRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, User.class);
    }

    @Override
    protected Query buildQueryFindOneByData(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User object must not be null");
        }

        Query query = new Query();

        // Add criteria for each non-null field
        if (StringUtils.hasText(user.getId())) {
            query.addCriteria(Criteria.where("id").is(new ObjectId(user.getId())));
        }

        if (StringUtils.hasText(user.getCompanyId())) {
            query.addCriteria(Criteria.where("companyId").is(new ObjectId(user.getCompanyId())));
        }

        if (StringUtils.hasText(user.getFirstName())) {
            query.addCriteria(Criteria.where("firstName").is(user.getFirstName()));
        }

        if (StringUtils.hasText(user.getLastName())) {
            query.addCriteria(Criteria.where("lastName").is(user.getLastName()));
        }

        if (StringUtils.hasText(user.getEmail())) {
            query.addCriteria(Criteria.where("email").is(user.getEmail()));
        }

        if (StringUtils.hasText(user.getEncryptedPassword())) {
            query.addCriteria(Criteria.where("encryptedPassword").is(user.getEncryptedPassword()));
        }

        if (StringUtils.hasText(user.getAccountStatus())) {
            query.addCriteria(Criteria.where("accountStatus").is(user.getAccountStatus()));
        }

        if (user.getCreatedAt() != null) {
            query.addCriteria(Criteria.where("createdAt").is(user.getCreatedAt()));
        }

        if (user.getLastUpdatedAt() != null) {
            query.addCriteria(Criteria.where("lastUpdatedAt").is(user.getLastUpdatedAt()));
        }

        return query;
    }

    @Override
    protected Update mapFieldUpdateOne(User entity) {
        var update = super.mapFieldUpdateOne(entity);

        if (entity.getFirstName() != null) {
            update.set("firstName", entity.getFirstName());
        }
        if (entity.getLastName() != null) {
            update.set("lastName", entity.getLastName());
        }
        if (entity.getEmail() != null) {
            update.set("email", entity.getEmail());
        }
        if (entity.getEncryptedPassword() != null) {
            update.set("encryptedPassword", entity.getEncryptedPassword());
        }
        if (entity.getAccountStatus() != null) {
            update.set("accountStatus", entity.getAccountStatus());
        }

        update.set("lastUpdatedAt", Instant.now());

        return update;
    }
}
