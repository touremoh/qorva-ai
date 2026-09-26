package ai.qorva.core.dao.querybuilder;

import ai.qorva.core.dao.entity.QorvaEntity;
import ai.qorva.core.dao.specifications.MongoSpecification;

import java.util.Map;

/**
 * Turns a resource's list/search params into a query. The tenant criterion is not this builder's job:
 * the generic service always adds its own {@code inTenantScope()} on top of whatever is built here.
 */
public interface QorvaQueryBuilder<E extends QorvaEntity> {

	MongoSpecification<E> buildQuery(Map<String, String> params);
}
