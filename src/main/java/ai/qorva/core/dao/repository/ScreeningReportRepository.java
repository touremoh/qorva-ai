package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.ScreeningReport;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Objects;

@Repository
public class ScreeningReportRepository extends AbstractQorvaRepository<ScreeningReport> {

    // Constants for field names
    private static final String FIELD_REPORT_NAME = "reportName";
    private static final String FIELD_COMPANY_ID = "companyId";
    private static final String FIELD_REPORT_DETAILS = "reportDetails";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_LAST_UPDATED_AT = "lastUpdatedAt";

    @Autowired
    public ScreeningReportRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, ScreeningReport.class);
    }

    @Override
    protected Query buildQueryFindOneByData(String companyId, ScreeningReport screeningReport) {
        if (screeningReport == null) {
            throw new IllegalArgumentException("ScreeningReport object must not be null");
        }

        Query query = new Query();

        if (StringUtils.hasText(screeningReport.getId())) {
            query.addCriteria(Criteria.where(FIELD_ID).is(new ObjectId(screeningReport.getId())));
        }

        if (StringUtils.hasText(screeningReport.getCompanyId())) {
            query.addCriteria(Criteria.where(FIELD_COMPANY_ID).is(new ObjectId(screeningReport.getCompanyId())));
        }

        if (StringUtils.hasText(screeningReport.getReportName())) {
            query.addCriteria(Criteria.where(FIELD_REPORT_NAME).is(screeningReport.getReportName()));
        }

        if (Objects.nonNull(screeningReport.getCreatedAt())) {
            query.addCriteria(Criteria.where(FIELD_CREATED_AT).is(screeningReport.getCreatedAt()));
        }

        if (Objects.nonNull(screeningReport.getLastUpdatedAt())) {
            query.addCriteria(Criteria.where(FIELD_LAST_UPDATED_AT).is(screeningReport.getLastUpdatedAt()));
        }

        if (StringUtils.hasText(screeningReport.getStatus())) {
            query.addCriteria(Criteria.where(FIELD_STATUS).is(screeningReport.getStatus()));
        }

        return query;
    }

    @Override
    protected Update mapFieldsUpdateOne(ScreeningReport entity) {
        var update = super.mapFieldsUpdateOne(entity);

        if (StringUtils.hasText(entity.getReportName())) {
            update.set(FIELD_REPORT_NAME, entity.getReportName());
        }

        if (Objects.nonNull(entity.getReportDetails()) && !entity.getReportDetails().isEmpty()) {
            update.set(FIELD_REPORT_DETAILS, entity.getReportDetails());
        }

        if (StringUtils.hasText(entity.getCompanyId())) {
            update.set(FIELD_COMPANY_ID, new ObjectId(entity.getCompanyId()));
        }

        if (StringUtils.hasText(entity.getStatus())) {
            update.set(FIELD_STATUS, entity.getStatus());
        }

        update.set(FIELD_LAST_UPDATED_AT, Instant.now());

        return update;
    }

}
