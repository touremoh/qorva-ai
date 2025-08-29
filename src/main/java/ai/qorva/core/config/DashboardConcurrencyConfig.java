package ai.qorva.core.config;// e.g., in a @Configuration class
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class DashboardConcurrencyConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService dashboardExecutor() {
        // One virtual thread per task — perfect for I/O-bound DB calls
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
