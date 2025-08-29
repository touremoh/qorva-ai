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
}
