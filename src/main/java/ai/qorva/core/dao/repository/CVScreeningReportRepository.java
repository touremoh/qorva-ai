package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CVScreeningReport;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.MongoTemplate;

@Repository
public class CVScreeningReportRepository extends AbstractQorvaRepository<CVScreeningReport> {
    public CVScreeningReportRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, CVScreeningReport.class);
    }
}
