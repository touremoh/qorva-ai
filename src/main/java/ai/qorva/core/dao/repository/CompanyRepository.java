package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.Company;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.MongoTemplate;

@Repository
public class CompanyRepository extends AbstractQorvaRepository<Company> {
    public CompanyRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, Company.class);
    }
}
