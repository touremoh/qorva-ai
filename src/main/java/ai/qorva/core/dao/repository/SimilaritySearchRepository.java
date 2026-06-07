package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

public interface SimilaritySearchRepository {

    List<CV> similaritySearch(float[] queryEmbedding, ObjectId tenantId, Boolean filterOpenToWork, List<String> includedStatuses, int limit, Criteria postFilter);

    default List<CV> similaritySearch(float[] queryEmbedding, ObjectId tenantId, Boolean filterOpenToWork, List<String> includedStatuses, int limit) {
        return similaritySearch(queryEmbedding, tenantId, filterOpenToWork, includedStatuses, limit, null);
    }
}
