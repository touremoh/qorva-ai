package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.Company;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.util.StringUtils;

import static java.util.Objects.isNull;

@Repository
public class CompanyRepository extends AbstractQorvaRepository<Company> {
    public CompanyRepository(MongoTemplate mongoTemplate) {
        super(mongoTemplate, Company.class);
    }

    @Override
    protected Query buildQueryFindOneByData(String companyId, Company company) {
        if (isNull(company)) {
            throw new IllegalArgumentException("Company object must not be null");
        }

        Query query = new Query();

        if (StringUtils.hasText(company.getName())) {
            query.addCriteria(Criteria.where("name").is(company.getName()));
        }

        return query;
    }
}
