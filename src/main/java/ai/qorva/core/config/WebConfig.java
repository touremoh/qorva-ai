package ai.qorva.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Value("${weblink.allowedOrigin}")
	private String allowedOrigin;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")

			    // Only allowed origin will be accepted
			    .allowedOrigins(this.allowedOrigin)

			    // Accepted Http Request Methods
			    .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

			    // Allow properties in the header
				.allowedHeaders("*")

			    // Allow credentials
				.allowCredentials(true);
	}
}
