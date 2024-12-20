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
import java.util.Objects;

import static java.util.Objects.isNull;

@Repository
public class UserRepository extends AbstractQorvaRepository<User> {

    // Constants for field names
    private static final String FIELD_FIRST_NAME = "firstName";
    private static final String FIELD_LAST_NAME = "lastName";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_ENCRYPTED_PASSWORD = "encryptedPassword";
    private static final String FIELD_ACCOUNT_STATUS = "accountStatus";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_UPDATED_AT = "lastUpdatedAt";

    public UserRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, User.class);
    }

    @Override
    protected Query buildQueryFindOneByData(String companyId, User user) {
        if (isNull(user)) {
            throw new IllegalArgumentException("User object must not be null");
        }

        Query query = new Query();

        if (StringUtils.hasText(companyId)) {
            query.addCriteria(Criteria.where(FIELD_COMPANY_ID).is(companyId));
        }

        if (StringUtils.hasText(user.getId())) {
            query.addCriteria(Criteria.where(FIELD_ID).is(new ObjectId(user.getId())));
        }

        if (StringUtils.hasText(user.getFirstName())) {
            query.addCriteria(Criteria.where(FIELD_FIRST_NAME).is(user.getFirstName()));
        }

        if (StringUtils.hasText(user.getLastName())) {
            query.addCriteria(Criteria.where(FIELD_LAST_NAME).is(user.getLastName()));
        }

        if (StringUtils.hasText(user.getEmail())) {
            query.addCriteria(Criteria.where(FIELD_EMAIL).is(user.getEmail()));
        }

        if (StringUtils.hasText(user.getEncryptedPassword())) {
            query.addCriteria(Criteria.where(FIELD_ENCRYPTED_PASSWORD).is(user.getEncryptedPassword()));
        }

        if (StringUtils.hasText(user.getAccountStatus())) {
            query.addCriteria(Criteria.where(FIELD_ACCOUNT_STATUS).is(user.getAccountStatus()));
        }

        if (Objects.nonNull(user.getCreatedAt())) {
            query.addCriteria(Criteria.where(FIELD_CREATED_AT).is(user.getCreatedAt()));
        }

        if (Objects.nonNull(user.getLastUpdatedAt())) {
            query.addCriteria(Criteria.where(FIELD_LAST_UPDATED_AT).is(user.getLastUpdatedAt()));
        }

        return query;
    }

    @Override
    protected Update mapFieldsUpdateOne(User entity) {
        var update = super.mapFieldsUpdateOne(entity);

        if (StringUtils.hasText(entity.getFirstName())) {
            update.set(FIELD_FIRST_NAME, entity.getFirstName());
        }
        if (StringUtils.hasText(entity.getLastName())) {
            update.set(FIELD_LAST_NAME, entity.getLastName());
        }
        if (StringUtils.hasText(entity.getEmail())) {
            update.set(FIELD_EMAIL, entity.getEmail());
        }
        if (StringUtils.hasText(entity.getEncryptedPassword())) {
            update.set(FIELD_ENCRYPTED_PASSWORD, entity.getEncryptedPassword());
        }
        if (StringUtils.hasText(entity.getAccountStatus())) {
            update.set(FIELD_ACCOUNT_STATUS, entity.getAccountStatus());
        }

        update.set(FIELD_LAST_UPDATED_AT, Instant.now());

        return update;
    }
}
