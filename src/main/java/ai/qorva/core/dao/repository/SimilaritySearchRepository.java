package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

public interface SimilaritySearchRepository {

    /** Lowest similarity ({@code vectorSearchScore}, cosine mapped to 0..1) a candidate needs to be matched. */
    double MIN_MATCH_SCORE = 0.5;

    List<CV> similaritySearch(float[] queryEmbedding, ObjectId tenantId, Boolean filterOpenToWork, List<String> includedStatuses, int limit, Criteria postFilter);

    default List<CV> similaritySearch(float[] queryEmbedding, ObjectId tenantId, Boolean filterOpenToWork, List<String> includedStatuses, int limit) {
        return similaritySearch(queryEmbedding, tenantId, filterOpenToWork, includedStatuses, limit, null);
    }
}
