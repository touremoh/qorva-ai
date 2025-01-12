package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Objects;

@Repository
public class CVRepository extends AbstractQorvaRepository<CV> {

    // Constants for field names
    private static final String FIELD_PERSONAL_INFORMATION = "personalInformation";
    private static final String FIELD_KEY_SKILLS = "keySkills";
    private static final String FIELD_PROFILES = "profiles";
    private static final String FIELD_WORK_EXPERIENCE = "workExperience";
    private static final String FIELD_EDUCATION = "education";
    private static final String FIELD_CERTIFICATIONS = "certifications";
    private static final String FIELD_SKILLS_AND_QUALIFICATIONS = "skillsAndQualifications";
    private static final String FIELD_PROJECTS_AND_ACHIEVEMENTS = "projectsAndAchievements";
    private static final String FIELD_INTERESTS_AND_HOBBIES = "interestsAndHobbies";
    private static final String FIELD_REFERENCES = "references";
    private static final String FIELD_ATTACHMENT = "attachment";
    private static final String FIELD_TAGS = "tags";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_UPDATED_AT = "lastUpdatedAt";

    @Autowired
    public CVRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, CV.class);
    }

    @Override
    protected Query buildQueryFindMany(String companyId, int pageNumber, int pageSize) {
        return new Query(Criteria.where(FIELD_COMPANY_ID).is(companyId))
            .with(Sort.by(Sort.Direction.DESC, FIELD_LAST_UPDATED_AT)) // Add sorting by lastUpdateTimestamp in descending order
            .skip((long) pageNumber * pageSize)
            .limit(pageSize);
    }

    @Override
    protected Query buildQueryFindOneByData(String companyId, CV cv) {
        if (cv == null) {
            throw new IllegalArgumentException("CV object must not be null");
        }

        Query query = new Query();

        if (StringUtils.hasText(cv.getId())) {
            query.addCriteria(Criteria.where(FIELD_ID).is(new ObjectId(cv.getId())));
        }

        if (StringUtils.hasText(cv.getCompanyId())) {
            query.addCriteria(Criteria.where(FIELD_COMPANY_ID).is(new ObjectId(cv.getCompanyId())));
        }

        if (Objects.nonNull(cv.getPersonalInformation())) {
            query.addCriteria(Criteria.where(FIELD_PERSONAL_INFORMATION).is(cv.getPersonalInformation()));
        }

        if (Objects.nonNull(cv.getKeySkills())) {
            query.addCriteria(Criteria.where(FIELD_KEY_SKILLS).is(cv.getKeySkills()));
        }

        if (Objects.nonNull(cv.getProfiles())) {
            query.addCriteria(Criteria.where(FIELD_PROFILES).is(cv.getProfiles()));
        }

        if (Objects.nonNull(cv.getCreatedAt())) {
            query.addCriteria(Criteria.where(FIELD_CREATED_AT).is(cv.getCreatedAt()));
        }

        if (Objects.nonNull(cv.getLastUpdatedAt())) {
            query.addCriteria(Criteria.where(FIELD_LAST_UPDATED_AT).is(cv.getLastUpdatedAt()));
        }

        return query;
    }

    @Override
    protected Update mapFieldsUpdateOne(CV entity) {
        var update = super.mapFieldsUpdateOne(entity);

        if (Objects.nonNull(entity.getPersonalInformation())) {
            update.set(FIELD_PERSONAL_INFORMATION, entity.getPersonalInformation());
        }

        if (Objects.nonNull(entity.getKeySkills())) {
            update.set(FIELD_KEY_SKILLS, entity.getKeySkills());
        }

        if (Objects.nonNull(entity.getProfiles())) {
            update.set(FIELD_PROFILES, entity.getProfiles());
        }

        if (Objects.nonNull(entity.getWorkExperience())) {
            update.set(FIELD_WORK_EXPERIENCE, entity.getWorkExperience());
        }

        if (Objects.nonNull(entity.getEducation())) {
            update.set(FIELD_EDUCATION, entity.getEducation());
        }

        if (Objects.nonNull(entity.getCertifications())) {
            update.set(FIELD_CERTIFICATIONS, entity.getCertifications());
        }

        if (Objects.nonNull(entity.getSkillsAndQualifications())) {
            update.set(FIELD_SKILLS_AND_QUALIFICATIONS, entity.getSkillsAndQualifications());
        }

        if (Objects.nonNull(entity.getProjectsAndAchievements())) {
            update.set(FIELD_PROJECTS_AND_ACHIEVEMENTS, entity.getProjectsAndAchievements());
        }

        if (Objects.nonNull(entity.getInterestsAndHobbies())) {
            update.set(FIELD_INTERESTS_AND_HOBBIES, entity.getInterestsAndHobbies());
        }

        if (Objects.nonNull(entity.getReferences())) {
            update.set(FIELD_REFERENCES, entity.getReferences());
        }

        if (Objects.nonNull(entity.getAttachment())) {
            update.set(FIELD_ATTACHMENT, entity.getAttachment());
        }

        if (Objects.nonNull(entity.getTags())) {
            update.set(FIELD_TAGS, entity.getTags());
        }

        update.set(FIELD_LAST_UPDATED_AT, Instant.now());

        return update;
    }
}
