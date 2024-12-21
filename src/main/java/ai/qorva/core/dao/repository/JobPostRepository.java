package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.JobPost;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Objects;

@Repository
public class JobPostRepository extends AbstractQorvaRepository<JobPost> {

    // Constants for field names;
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_UPDATED_AT = "lastUpdatedAt";
    private static final String FIELD_CREATED_BY = "createdBy";
    private static final String FIELD_LAST_UPDATED_BY = "lastUpdatedBy";

    public JobPostRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, JobPost.class);
    }

    @Override
    protected Query buildQueryFindOneByData(String companyId, JobPost jobPost) {
        if (jobPost == null) {
            throw new IllegalArgumentException("JobPost object must not be null");
        }

        Query query = new Query();

        if (StringUtils.hasText(jobPost.getId())) {
            query.addCriteria(Criteria.where(FIELD_ID).is(new ObjectId(jobPost.getId())));
        }

        if (StringUtils.hasText(jobPost.getCompanyId())) {
            query.addCriteria(Criteria.where(FIELD_COMPANY_ID).is(new ObjectId(jobPost.getCompanyId())));
        }

        if (StringUtils.hasText(jobPost.getTitle())) {
            query.addCriteria(Criteria.where(FIELD_TITLE).is(jobPost.getTitle()));
        }

        if (StringUtils.hasText(jobPost.getDescription())) {
            query.addCriteria(Criteria.where(FIELD_DESCRIPTION).is(jobPost.getDescription()));
        }

        if (Objects.nonNull(jobPost.getCreatedAt())) {
            query.addCriteria(Criteria.where(FIELD_CREATED_AT).is(jobPost.getCreatedAt()));
        }

        if (Objects.nonNull(jobPost.getLastUpdatedAt())) {
            query.addCriteria(Criteria.where(FIELD_LAST_UPDATED_AT).is(jobPost.getLastUpdatedAt()));
        }

        if (StringUtils.hasText(jobPost.getCreatedBy())) {
            query.addCriteria(Criteria.where(FIELD_CREATED_BY).is(new ObjectId(jobPost.getCreatedBy())));
        }

        if (StringUtils.hasText(jobPost.getLastUpdatedBy())) {
            query.addCriteria(Criteria.where(FIELD_LAST_UPDATED_BY).is(new ObjectId(jobPost.getLastUpdatedBy())));
        }

        return query;
    }

    @Override
    protected Update mapFieldsUpdateOne(JobPost entity) {
        var update = super.mapFieldsUpdateOne(entity);

        if (StringUtils.hasText(entity.getTitle())) {
            update.set(FIELD_TITLE, entity.getTitle());
        }

        if (StringUtils.hasText(entity.getDescription())) {
            update.set(FIELD_DESCRIPTION, entity.getDescription());
        }

        if (StringUtils.hasText(entity.getCompanyId())) {
            update.set(FIELD_COMPANY_ID, new ObjectId(entity.getCompanyId()));
        }

        if (StringUtils.hasText(entity.getCreatedBy())) {
            update.set(FIELD_CREATED_BY, new ObjectId(entity.getCreatedBy()));
        }

        if (StringUtils.hasText(entity.getLastUpdatedBy())) {
            update.set(FIELD_LAST_UPDATED_BY, new ObjectId(entity.getLastUpdatedBy()));
        }

        update.set(FIELD_LAST_UPDATED_AT, Instant.now());

        return update;
    }
}
