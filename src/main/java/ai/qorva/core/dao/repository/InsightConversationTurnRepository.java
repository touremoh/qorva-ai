package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.InsightConversationTurn;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InsightConversationTurnRepository extends MongoRepository<InsightConversationTurn, ObjectId> {

    List<InsightConversationTurn> findByConversationIdAndTenantIdAndInitiatedByOrderByCreatedAtAsc(String conversationId, String tenantId, String initiatedBy);

    List<InsightConversationTurn> findByTenantIdAndInitiatedByOrderByCreatedAtAsc(String tenantId, String initiatedBy);

    void deleteByConversationIdAndTenantIdAndInitiatedBy(String conversationId, String tenantId, String initiatedBy);

    /** Deletes every insight conversation turn belonging to a tenant (used when purging demo data on upgrade). */
    long deleteByTenantId(String tenantId);
}
