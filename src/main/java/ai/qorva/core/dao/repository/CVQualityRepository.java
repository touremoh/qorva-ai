package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.enums.QualityIssueKeyEnum;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Drill-down queries for the Library Quality feature: pages of CVs affected by a given issue.
 * Implementations must project out heavy fields (attachment, rawText, embedding).
 */
public interface CVQualityRepository {

	Page<CV> findQualityIssueCVs(ObjectId tenantId, QualityIssueKeyEnum issueKey, Pageable pageable);
}
