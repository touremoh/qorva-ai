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

	/** {@code qorva.admin.allowed-origins}: the admin console's origins (none while it runs behind the Vite proxy). */
	@Value("#{'${qorva.admin.allowed-origins:}'.split(',')}")
	private List<String> adminAllowedOrigins;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		// Registered first so it wins over "/**": the admin API answers only the admin console's origins,
		// never the app's or the landing page's. Bearer tokens, no cookies: no credentials.
		registry.addMapping("/admin/**")
			.allowedOrigins(adminAllowedOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toArray(String[]::new))
			.allowedMethods("*")
			.allowedHeaders("*");

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
