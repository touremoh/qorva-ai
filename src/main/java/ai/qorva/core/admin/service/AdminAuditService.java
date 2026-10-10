package ai.qorva.core.admin.service;

import ai.qorva.core.admin.security.AdminContext;
import ai.qorva.core.dao.entity.AdminAuditLog;
import ai.qorva.core.dao.repository.AdminAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

/** Records every change made from the admin console. A failure to record is logged, never hidden from the logs. */
@Slf4j
@Service
public class AdminAuditService {

	private final AdminAuditLogRepository repository;

	public AdminAuditService(AdminAuditLogRepository repository) {
		this.repository = repository;
	}

	public void record(String action, String tenantId, String userId, String summary) {
		var admin = AdminContext.current();
		record(admin.id(), admin.email(), action, tenantId, userId, summary);
	}

	/** For events without a signed-in admin (sign-in itself, set-password, the bootstrap). */
	public void record(String adminId, String adminEmail, String action, String tenantId, String userId, String summary) {
		try {
			repository.save(AdminAuditLog.builder()
				.at(Instant.now())
				.adminId(adminId)
				.adminEmail(adminEmail)
				.action(action)
				.targetTenantId(tenantId)
				.targetUserId(userId)
				.summary(summary)
				.ip(clientIp())
				.build());
		} catch (RuntimeException e) {
			log.error("Admin audit entry NOT recorded: action={} admin={} tenant={} user={} summary={}",
				action, adminEmail, tenantId, userId, summary, e);
		}
		log.info("admin_audit action={} admin={} tenant={} user={} summary={}", action, adminEmail, tenantId, userId, summary);
	}

	private static String clientIp() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return null;
		}
		HttpServletRequest request = attributes.getRequest();
		var forwarded = request.getHeader("X-Forwarded-For");
		return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
	}
}
