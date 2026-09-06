package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.AtsOutboundTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface AtsOutboundTaskRepository extends MongoRepository<AtsOutboundTask, String> {

	List<AtsOutboundTask> findByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
		List<String> statuses, Instant now, Pageable pageable);

	long deleteByConnectionId(String connectionId);
}
