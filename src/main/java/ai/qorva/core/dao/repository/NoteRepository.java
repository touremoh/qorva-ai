package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.Note;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface NoteRepository extends MongoRepository<Note, String>, OwnedLookup<Note> {

	List<Note> findByTenantIdAndTargetTypeAndTargetIdOrderByCreatedAtDesc(String tenantId, String targetType, String targetId);

	long deleteByTenantIdAndTargetTypeAndTargetId(String tenantId, String targetType, String targetId);

	long deleteByTenantIdAndTargetTypeAndTargetIdIn(String tenantId, String targetType, Collection<String> targetIds);

	long deleteByTenantId(String tenantId);
}
