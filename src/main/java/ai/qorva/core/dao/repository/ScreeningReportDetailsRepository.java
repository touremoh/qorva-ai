package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.ScreeningReportDetails;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Objects;

@Repository
public class ScreeningReportDetailsRepository extends AbstractQorvaRepository<ScreeningReportDetails> {

    // Constants for field names
    private static final String FIELD_COMPANY_ID = "companyId";
    private static final String FIELD_JOB_TITLE = "jobTitle";
    private static final String FIELD_CANDIDATE_NAME = "candidateName";
    private static final String FIELD_SKILLS_MATCH = "skillsMatch";
    private static final String FIELD_EXCEEDS_REQUIREMENTS = "exceedsRequirements";
    private static final String FIELD_LACKING_SKILLS = "lackingSkills";
    private static final String FIELD_EXPERIENCE_ALIGNMENT = "experienceAlignment";
    private static final String FIELD_OVERALL_SUMMARY = "overallSummary";
    private static final String FIELD_INTERVIEW_QUESTIONS = "interviewQuestions";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_UPDATED_AT = "lastUpdatedAt";

    @Autowired
    public ScreeningReportDetailsRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, ScreeningReportDetails.class);
    }

    @Override
    protected Query buildQueryFindOneByData(String companyId, ScreeningReportDetails screeningReportDetails) {
        if (screeningReportDetails == null) {
            throw new IllegalArgumentException("ScreeningReportDetails object must not be null");
        }

        Query query = new Query();

        if (StringUtils.hasText(screeningReportDetails.getId())) {
            query.addCriteria(Criteria.where(FIELD_ID).is(new ObjectId(screeningReportDetails.getId())));
        }

        if (StringUtils.hasText(screeningReportDetails.getCompanyId())) {
            query.addCriteria(Criteria.where(FIELD_COMPANY_ID).is(new ObjectId(screeningReportDetails.getCompanyId())));
        }

        if (StringUtils.hasText(screeningReportDetails.getJobTitle())) {
            query.addCriteria(Criteria.where(FIELD_JOB_TITLE).is(screeningReportDetails.getJobTitle()));
        }

        if (StringUtils.hasText(screeningReportDetails.getCandidateName())) {
            query.addCriteria(Criteria.where(FIELD_CANDIDATE_NAME).is(screeningReportDetails.getCandidateName()));
        }

        return query;
    }

    @Override
    protected Update mapFieldsUpdateOne(ScreeningReportDetails entity) {
        var update = super.mapFieldsUpdateOne(entity);

        if (StringUtils.hasText(entity.getJobTitle())) {
            update.set(FIELD_JOB_TITLE, entity.getJobTitle());
        }

        if (StringUtils.hasText(entity.getCandidateName())) {
            update.set(FIELD_CANDIDATE_NAME, entity.getCandidateName());
        }

        if (Objects.nonNull(entity.getSkillsMatch())) {
            update.set(FIELD_SKILLS_MATCH, entity.getSkillsMatch());
        }

        if (Objects.nonNull(entity.getExceedsRequirements())) {
            update.set(FIELD_EXCEEDS_REQUIREMENTS, entity.getExceedsRequirements());
        }

        if (Objects.nonNull(entity.getLackingSkills())) {
            update.set(FIELD_LACKING_SKILLS, entity.getLackingSkills());
        }

        if (Objects.nonNull(entity.getExperienceAlignment())) {
            update.set(FIELD_EXPERIENCE_ALIGNMENT, entity.getExperienceAlignment());
        }

        if (Objects.nonNull(entity.getOverallSummary())) {
            update.set(FIELD_OVERALL_SUMMARY, entity.getOverallSummary());
        }

        if (Objects.nonNull(entity.getInterviewQuestions())) {
            update.set(FIELD_INTERVIEW_QUESTIONS, entity.getInterviewQuestions());
        }

        update.set(FIELD_LAST_UPDATED_AT, Instant.now());

        return update;
    }
}
