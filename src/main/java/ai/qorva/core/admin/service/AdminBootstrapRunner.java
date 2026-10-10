package ai.qorva.core.admin.service;

import ai.qorva.core.admin.config.AdminProperties;
import ai.qorva.core.dao.entity.PlatformAdmin;
import ai.qorva.core.dao.repository.PlatformAdminRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin (OWNER) when the admin API is on, no admin exists yet and
 * {@code QORVA_ADMIN_BOOTSTRAP_EMAIL} is set. The admin gets a set-password link by email; no password ever
 * travels through configuration or logs. A failure is logged and never stops the application.
 */
@Slf4j
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

	private final AdminProperties properties;
	private final PlatformAdminRepository admins;
	private final AdminAccountService accounts;
	private final AdminAuditService audit;

	public AdminBootstrapRunner(AdminProperties properties, PlatformAdminRepository admins, AdminAccountService accounts, AdminAuditService audit) {
		this.properties = properties;
		this.admins = admins;
		this.accounts = accounts;
		this.audit = audit;
	}

	@Override
	public void run(ApplicationArguments args) {
		var email = AdminAuthService.normalize(properties.getBootstrapEmail());
		if (!properties.isEnabled() || email == null) {
			return;
		}
		try {
			if (admins.count() > 0) {
				log.info("Admin bootstrap skipped: admins already exist (QORVA_ADMIN_BOOTSTRAP_EMAIL can be removed)");
				return;
			}
			var admin = accounts.invite(email, null, null, PlatformAdmin.ROLE_OWNER, "bootstrap");
			audit.record(admin.getId(), email, "ADMIN_BOOTSTRAPPED", null, null, "First admin created; set-password link emailed");
			log.info("Admin bootstrap: OWNER {} created, set-password link emailed", email);
		} catch (Exception e) {
			log.error("Admin bootstrap failed for {}", email, e);
		}
	}
}
