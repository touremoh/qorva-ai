package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dto.common.CompanyInfo;
import ai.qorva.core.dto.common.SubscriptionInfo;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.isNull;

@Repository
public class UserRepository extends AbstractQorvaRepository<User> {

    // Constants for field names
    private static final String FIELD_ID = "id";
    private static final String FIELD_COMPANY_ID = "tenantId";
    private static final String FIELD_FIRST_NAME = "firstName";
    private static final String FIELD_LAST_NAME = "lastName";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_ENCRYPTED_PASSWORD = "encryptedPassword";
    private static final String FIELD_ACCOUNT_STATUS = "userAccountStatus";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_UPDATED_AT = "lastUpdatedAt";
    private static final String FIELD_COMPANY_INFO = "companyInfo";
    private static final String FIELD_SUBSCRIPTION_INFO = "subscriptionInfo";

    @Autowired
    public UserRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, User.class);
    }

    @Override
    protected Query buildQueryFindOneByData(User user) {
        if (isNull(user)) {
            throw new IllegalArgumentException("User object must not be null");
        }

        Query query = new Query();

        if (StringUtils.hasText(user.getTenantId())) {
            query.addCriteria(Criteria.where(FIELD_COMPANY_ID).is(user.getTenantId()));
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

        if (StringUtils.hasText(user.getUserAccountStatus())) {
            query.addCriteria(Criteria.where(FIELD_ACCOUNT_STATUS).is(user.getUserAccountStatus()));
        }

        if (Objects.nonNull(user.getCompanyInfo())) {
            CompanyInfo info = user.getCompanyInfo();
            if (StringUtils.hasText(info.name())) {
                query.addCriteria(Criteria.where(FIELD_COMPANY_INFO + ".name").is(info.name()));
            }
            if (StringUtils.hasText(info.tenantId())) {
                query.addCriteria(Criteria.where(FIELD_COMPANY_INFO + ".tenantId").is(info.tenantId()));
            }
        }

        if (Objects.nonNull(user.getSubscriptionInfo())) {
            SubscriptionInfo sub = user.getSubscriptionInfo();
            if (StringUtils.hasText(sub.getSubscriptionPlan())) {
                query.addCriteria(Criteria.where(FIELD_SUBSCRIPTION_INFO + ".subscriptionPlan").is(sub.getSubscriptionPlan()));
            }
            if (StringUtils.hasText(sub.getSubscriptionStatus())) {
                query.addCriteria(Criteria.where(FIELD_SUBSCRIPTION_INFO + ".subscriptionStatus").is(sub.getSubscriptionStatus()));
            }
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
        if (StringUtils.hasText(entity.getUserAccountStatus())) {
            update.set(FIELD_ACCOUNT_STATUS, entity.getUserAccountStatus());
        }

        if (Objects.nonNull(entity.getCompanyInfo())) {
            update.set(FIELD_COMPANY_INFO, entity.getCompanyInfo());
        }

        if (Objects.nonNull(entity.getSubscriptionInfo())) {
            update.set(FIELD_SUBSCRIPTION_INFO, entity.getSubscriptionInfo());
        }

        update.set(FIELD_LAST_UPDATED_AT, Instant.now());

        return update;
    }

    public Optional<User> findOneByEmail(String email) {
        return Optional.ofNullable(this.mongoTemplate.findOne(new Query(Criteria.where(FIELD_EMAIL).is(email)), User.class));
    }
}
