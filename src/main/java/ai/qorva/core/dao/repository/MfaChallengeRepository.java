package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.MfaChallenge;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;

public interface MfaChallengeRepository extends MongoRepository<MfaChallenge, String> {

	long countByUserIdAndCreatedAtAfter(String userId, Instant after);
}
