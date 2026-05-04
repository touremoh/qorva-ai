package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.enums.ChatStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.*;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatsRepository extends QorvaRepository<Chat> {

    @Query(value = "{ 'tenantId': ?0, 'participants.userId': ?1 }")
    Page<Chat> findByTenantAndParticipant(String tenantId, String userId, Pageable pageable);

    @Query(value = "{ 'tenantId': ?0, 'status': ?1 }")
    Page<Chat> findByTenantAndStatus(String tenantId, ChatStatus status, Pageable pageable);

    @Query(value = "{ 'tenantId': ?0, '_id': ?1 }")
    Chat findOneByTenantAndId(String tenantId, String chatId);

    /**
     * Deletes chats associated with a specific tenant and context job post ID.
     * @param tenantId The tenant ID.
     * @param contextJobPostId The context job post ID.
     * @return The number of deleted chats.
     */
    long deleteByTenantIdAndContextJobPostId(String tenantId, String contextJobPostId);

    /**
     * Deletes chats associated with a specific tenant and context CV ID.
     * @param tenantId The tenant ID.
     * @param contextCVId The context CV ID.
     * @return The number of deleted chats.
     */
    long deleteByTenantIdAndContextCvId(String tenantId, String contextCVId);

    /**
     * Deletes chats associated with a specific tenant and context resume match ID.
     * @param tenantId The tenant ID.
     * @param contextResumeMatchId The context resume match ID.
     * @return The number of deleted chats.
     */
    long deleteByTenantIdAndContextResumeMatchId(String tenantId, String contextResumeMatchId);
}
