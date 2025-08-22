package ai.qorva.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Value("#{'${weblink.allowedOrigins}'.split(',')}")
	private List<String> allowedOrigins;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")

			    // Only allowed origin will be accepted
			    .allowedOrigins(allowedOrigins.toArray(new String[0]))

			    // Accepted Http Request Methods
			    .allowedMethods("*")

			    // Allow properties in the header
				.allowedHeaders("*")

			    // Allow credentials
				.allowCredentials(true);
	}
}
