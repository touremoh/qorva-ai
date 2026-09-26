package ai.qorva.core.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

/**
 * Wires {@link TenantScope}: whether a missing tenant fails or only warns
 * ({@code qorva.tenancy.fail-closed}), and propagation into {@code @Async} work.
 */
@Slf4j
@Configuration
public class TenantScopeConfiguration {

	@Value("${qorva.tenancy.fail-closed:false}")
	private boolean failClosed;

	@PostConstruct
	void apply() {
		TenantScope.setFailClosed(failClosed);
		log.info("Tenant isolation: {}", failClosed
			? "fail-closed (tenant-scoped code without a tenant is refused)"
			: "log-only (tenant-scoped code without a tenant is logged, then allowed)");
	}

	/** Applied by Spring Boot to the application task executor behind {@code @Async}. */
	@Bean
	public TaskDecorator tenantScopeTaskDecorator() {
		return TenantScope::wrap;
	}
}
