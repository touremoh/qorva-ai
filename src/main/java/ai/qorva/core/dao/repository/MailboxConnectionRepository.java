package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.MailboxConnection;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MailboxConnectionRepository extends MongoRepository<MailboxConnection, String> {

	Optional<MailboxConnection> findByTenantIdAndUserId(String tenantId, String userId);

	long deleteByTenantIdAndUserId(String tenantId, String userId);
}
