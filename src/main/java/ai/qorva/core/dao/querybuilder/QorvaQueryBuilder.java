package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dao.entity.QorvaEntity;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dao.specifications.MongoSpecifications;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Map;

public interface QorvaQueryBuilder<E extends QorvaEntity> {

	default MongoSpecification<E> buildQuery(Map<String, String> params) {
		String tenantId = params == null ? null : params.get("tenantId");
		if (tenantId == null || tenantId.isBlank()) {
			return MongoSpecifications.empty();
		}
		return () -> Criteria.where("tenantId").is(tenantId);
	}
}
