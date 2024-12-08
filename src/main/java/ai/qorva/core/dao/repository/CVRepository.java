package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.MongoTemplate;

@Repository
public class CVRepository extends AbstractQorvaRepository<CV> {
    public CVRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, CV.class);
    }
}
