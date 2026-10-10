package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.AdminMfaChallenge;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;

public interface AdminMfaChallengeRepository extends MongoRepository<AdminMfaChallenge, String> {

	long countByAdminIdAndCreatedAtAfter(String adminId, Instant after);
}
