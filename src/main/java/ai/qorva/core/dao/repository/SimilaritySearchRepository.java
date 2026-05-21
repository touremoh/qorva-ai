package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.CV;
import org.bson.types.ObjectId;

import java.util.List;

public interface SimilaritySearchRepository {

    List<CV> similaritySearch(float[] queryEmbedding, ObjectId tenantId, Boolean filterOpenToWork, List<String> excludedStatuses);
}
