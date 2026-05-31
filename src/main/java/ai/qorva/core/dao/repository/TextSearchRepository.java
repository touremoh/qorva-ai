package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import org.bson.types.ObjectId;

import java.util.List;

public interface TextSearchRepository {

    List<CV> textSearch(List<String> textTerms, List<String> industryTerms, ObjectId tenantId, int limit);

    long textSearchCount(List<String> textTerms, List<String> industryTerms, ObjectId tenantId);
}
