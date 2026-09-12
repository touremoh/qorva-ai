package ai.qorva.core.dao.repository;

import ai.qorva.core.dto.CVFilterOptionsData;
import org.bson.types.ObjectId;

/** Facet aggregation feeding the CV list filter rail. One round trip per call, tenant-scoped. */
public interface CVFilterOptionsRepository {

	/** Distinct filter values with counts over the tenant's active (or archived) CVs. */
	CVFilterOptionsData filterOptions(ObjectId tenantId, boolean archived);
}
