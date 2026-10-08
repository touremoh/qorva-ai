package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.SupportTicket;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SupportTicketRepository extends QorvaRepository<SupportTicket> {

	long countByTenantIdAndUserEmailAndCreatedAtAfter(String tenantId, String userEmail, Instant after);

	/** Retry sweep (system scope): tickets whose email has not gone out yet, created before {@code before}. */
	List<SupportTicket> findTop50ByEmailStatusAndEmailAttemptsLessThanAndCreatedAtBeforeOrderByCreatedAtAsc(String emailStatus,
	                                                                                                        int maxAttempts, Instant before);
}
