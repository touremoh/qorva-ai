package ai.qorva.core.config;

import ai.qorva.core.service.ApplicationUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final JwtConfig jwtConfig;
	private final ApplicationUserDetailsService userDetailsService;

	@Autowired
	public SecurityConfig(JwtConfig jwtConfig, ApplicationUserDetailsService userDetailsService) {
		this.jwtConfig = jwtConfig;
		this.userDetailsService = userDetailsService;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
		return authenticationConfiguration.getAuthenticationManager();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtRequestFilter jwtRequestFilter) throws Exception {
		// Disable CSRF (since you are using stateless JWT)
		http.csrf(AbstractHttpConfigurer::disable)
			// Configure authorization for your endpoints
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(
					"/register/**",
					"/auth/login",
					"/auth/token/validate"
				).permitAll() // Publicly accessible routes
				.anyRequest().authenticated()
			)

			// Stateless session management (for JWT)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

			// Add JWT filter before UsernamePasswordAuthenticationFilter
			.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public JwtRequestFilter jwtRequestFilter() {
		return new JwtRequestFilter(userDetailsService, jwtConfig);
	}
}
