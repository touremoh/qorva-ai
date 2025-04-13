package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.JobApplication;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Repository
public class JobApplicationRepository extends AbstractQorvaRepository<JobApplication> {

    // Constants for field names
    private static final String FIELD_ID = "id";
    private static final String FIELD_COMPANY_ID = "tenantId";
    private static final String FIELD_JOB_POST_ID = "jobPostId";
    private static final String FIELD_CANDIDATE_ID = "candidateInfo.candidateId";
    private static final String FIELD_CANDIDATE_NAME = "candidateInfo.candidateName";
    private static final String FIELD_YEARS_EXPERIENCE = "candidateInfo.nbYearsExperience";
    private static final String FIELD_SKILLS = "candidateInfo.skills";
    private static final String FIELD_PROFILE_SUMMARY = "candidateInfo.candidateProfileSummary";
    private static final String FIELD_REPORT_DETAILS = "reportDetails";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_UPDATED_AT = "lastUpdatedAt";
    private static final String FIELD_CREATED_BY = "createdBy";
    private static final String FIELD_LAST_UPDATED_BY = "lastUpdatedBy";

    @Autowired
    public JobApplicationRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, JobApplication.class);
    }

    @Override
    protected Query buildQueryFindMany(String companyId, int pageNumber, int pageSize) {
        return new Query(Criteria.where(FIELD_COMPANY_ID).is(companyId))
            .with(Sort.by(Sort.Direction.DESC, FIELD_LAST_UPDATED_AT))
            .skip((long) pageNumber * pageSize)
            .limit(pageSize);
    }

    @Override
    protected Query buildQueryFindOneByData(JobApplication candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("JobApplication object must not be null");
        }

        Query query = new Query();

        if (StringUtils.hasText(candidate.getId())) {
            query.addCriteria(Criteria.where(FIELD_ID).is(new ObjectId(candidate.getId())));
        }

        if (StringUtils.hasText(candidate.getTenantId())) {
            query.addCriteria(Criteria.where(FIELD_COMPANY_ID).is(candidate.getTenantId()));
        }

        if (StringUtils.hasText(candidate.getJobPostId())) {
            query.addCriteria(Criteria.where(FIELD_JOB_POST_ID).is(candidate.getJobPostId()));
        }

        if (candidate.getCandidateInfo() != null) {
            if (StringUtils.hasText(candidate.getCandidateInfo().getCandidateId())) {
                query.addCriteria(Criteria.where(FIELD_CANDIDATE_ID).is(candidate.getCandidateInfo().getCandidateId()));
            }

            if (StringUtils.hasText(candidate.getCandidateInfo().getCandidateName())) {
                query.addCriteria(Criteria.where(FIELD_CANDIDATE_NAME).is(candidate.getCandidateInfo().getCandidateName()));
            }

            if (candidate.getCandidateInfo().getNbYearsExperience() > 0) {
                query.addCriteria(Criteria.where(FIELD_YEARS_EXPERIENCE).is(candidate.getCandidateInfo().getNbYearsExperience()));
            }

            if (candidate.getCandidateInfo().getSkills() != null) {
                query.addCriteria(Criteria.where(FIELD_SKILLS).is(candidate.getCandidateInfo().getSkills()));
            }

            if (StringUtils.hasText(candidate.getCandidateInfo().getCandidateProfileSummary())) {
                query.addCriteria(Criteria.where(FIELD_PROFILE_SUMMARY).is(candidate.getCandidateInfo().getCandidateProfileSummary()));
            }
        }

        if (StringUtils.hasText(candidate.getStatus())) {
            query.addCriteria(Criteria.where(FIELD_STATUS).is(candidate.getStatus()));
        }

        if (Objects.nonNull(candidate.getCreatedAt())) {
            query.addCriteria(Criteria.where(FIELD_CREATED_AT).is(candidate.getCreatedAt()));
        }

        if (Objects.nonNull(candidate.getLastUpdatedAt())) {
            query.addCriteria(Criteria.where(FIELD_LAST_UPDATED_AT).is(candidate.getLastUpdatedAt()));
        }

        if (StringUtils.hasText(candidate.getCreatedBy())) {
            query.addCriteria(Criteria.where(FIELD_CREATED_BY).is(new ObjectId(candidate.getCreatedBy())));
        }

        if (StringUtils.hasText(candidate.getLastUpdatedBy())) {
            query.addCriteria(Criteria.where(FIELD_LAST_UPDATED_BY).is(new ObjectId(candidate.getLastUpdatedBy())));
        }

        return query;
    }

    @Override
    protected Update mapFieldsUpdateOne(JobApplication entity) {
        var update = super.mapFieldsUpdateOne(entity);

        if (StringUtils.hasText(entity.getJobPostId())) {
            update.set(FIELD_JOB_POST_ID, entity.getJobPostId());
        }

        if (entity.getCandidateInfo() != null) {
            var info = entity.getCandidateInfo();

            if (StringUtils.hasText(info.getCandidateId())) {
                update.set(FIELD_CANDIDATE_ID, info.getCandidateId());
            }

            if (StringUtils.hasText(info.getCandidateName())) {
                update.set(FIELD_CANDIDATE_NAME, info.getCandidateName());
            }

            if (info.getNbYearsExperience() >= 0) {
                update.set(FIELD_YEARS_EXPERIENCE, info.getNbYearsExperience());
            }

            if (Objects.nonNull(info.getSkills())) {
                update.set(FIELD_SKILLS, info.getSkills());
            }

            if (StringUtils.hasText(info.getCandidateProfileSummary())) {
                update.set(FIELD_PROFILE_SUMMARY, info.getCandidateProfileSummary());
            }
        }

        if (StringUtils.hasText(entity.getStatus())) {
            update.set(FIELD_STATUS, entity.getStatus());
        }

        if (Objects.nonNull(entity.getAiAnalysisReportDetails())) {
            update.set(FIELD_REPORT_DETAILS, entity.getAiAnalysisReportDetails());
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

    public long countCVAnalyzedInMonth(String tenantId, LocalDate startOfMonth, LocalDate endOfMonth) {
        // Build query criteria
        var criteria = new Criteria().andOperator(
            Criteria.where(FIELD_COMPANY_ID).is(tenantId),
            Criteria.where(FIELD_CREATED_AT).gte(startOfMonth).lte(endOfMonth)
        );

        // Build query
        var query = new Query(criteria);

        // execute query and return results
        return this.mongoTemplate.count(query, JobApplication.class);
    }

    public Collection<JobApplication> savaAll(List<JobApplication> jobApplications) {
        return this.mongoTemplate.insertAll(jobApplications);
    }
}
