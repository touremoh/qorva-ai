package ai.qorva.core.config;

import ai.qorva.core.security.TenantScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class DashboardConcurrencyConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService dashboardExecutor() {
        // One virtual thread per task (I/O-bound DB calls), each running in the requesting tenant's scope
        return TenantScope.propagating(Executors.newVirtualThreadPerTaskExecutor());
    }
}
