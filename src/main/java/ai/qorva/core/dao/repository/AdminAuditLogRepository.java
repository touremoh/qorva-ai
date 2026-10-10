package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.AdminAuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminAuditLogRepository extends MongoRepository<AdminAuditLog, String> {
}
