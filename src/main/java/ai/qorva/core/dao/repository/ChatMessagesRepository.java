// ai/qorva/core/repository/ChatMessagesRepository.java
package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.ChatMessage;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.repository.*;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessagesRepository extends QorvaRepository<ChatMessage> {

    @Query(value = "{ 'tenantId': ?0, 'chatId': ?1, 'role': { $ne: ?2 }  }", sort = "{ 'createdAt': 1 }")
    Page<ChatMessage> findPageByTenantAndChatIdExcludingSystemMessage(String tenantId, String chatId, String role, Pageable pageable);

    @Query(value = "{ 'tenantId': ?0, 'chatId': ?1 }", sort = "{ 'createdAt': -1 }", fields = "{ '_id': 1 }")
    Page<ChatMessage> findIdsByTenantAndChatIdDesc(String tenantId, String chatId, Pageable pageable);

    long countByTenantIdAndChatId(String tenantId, String chatId);

    @Query(value = "{ 'tenantId': ?0, 'chatId': ?1 }", sort = "{ 'createdAt': 1 }", fields = "{ 'content': 1, 'role': 1 }")
    Iterable<ChatMessage> streamForContext(String tenantId, String chatId);
}
