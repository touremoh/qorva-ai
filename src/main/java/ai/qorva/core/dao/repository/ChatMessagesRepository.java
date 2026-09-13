// ai/qorva/core/repository/ChatMessagesRepository.java
package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.ChatMessage;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.repository.*;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessagesRepository extends QorvaRepository<ChatMessage> {

    @Query(value = "{ 'tenantId': ?0, 'chatId': ?1, 'role': { $ne: ?2 }  }")
    Page<ChatMessage> findPageByTenantAndChatIdExcludingSystemMessage(String tenantId, String chatId, String role, Pageable pageable);

    @Query(value = "{ 'tenantId': ?0, 'chatId': ?1 }", sort = "{ 'createdAt': -1 }", fields = "{ '_id': 1 }")
    Page<ChatMessage> findIdsByTenantAndChatIdDesc(String tenantId, String chatId, Pageable pageable);

    long countByTenantIdAndChatId(String tenantId, String chatId);

    ChatMessage findFirstByTenantIdAndChatIdOrderByCreatedAtDesc(String tenantId, String chatId);

    long deleteByTenantIdAndChatId(String tenantId, String chatId);

    /** Deletes every chat message belonging to a tenant (used when purging demo data on upgrade). */
    long deleteByTenantId(String tenantId);

    long deleteByTenantIdAndChatIdIn(String tenantId, java.util.Collection<String> chatIds);

    @Query(value = "{ 'tenantId': ?0, 'chatId': ?1, 'role': { $ne: ?2 } }", sort = "{ 'createdAt': 1 }", fields = "{ 'content': 1, 'role': 1, 'createdAt': 1 }")
    Iterable<ChatMessage> streamForContext(String tenantId, String chatId, String excludedRole);

    /** Messages after the summary cut-off — the only ones still eligible for the verbatim window. */
    @Query(value = "{ 'tenantId': ?0, 'chatId': ?1, 'role': { $ne: ?2 }, 'createdAt': { $gt: ?3 } }", sort = "{ 'createdAt': 1 }", fields = "{ 'content': 1, 'role': 1, 'createdAt': 1 }")
    Iterable<ChatMessage> streamForContextAfter(String tenantId, String chatId, String excludedRole, java.time.Instant after);
}
