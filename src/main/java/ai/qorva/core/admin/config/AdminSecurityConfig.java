package ai.qorva.core.admin.config;

import ai.qorva.core.admin.security.AdminErrorWriter;
import ai.qorva.core.admin.security.AdminJwtFilter;
import ai.qorva.core.admin.security.AdminTokens;
import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dao.repository.PlatformAdminRepository;
import ai.qorva.core.exception.QorvaErrorCodes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/**
 * The admin console's own security chain, matched before the tenant chain. Admin tokens only; a tenant token
 * gets 401 here. Off ({@code qorva.admin.enabled=false}) → every admin route answers 404.
 */
@Slf4j
@Configuration
public class AdminSecurityConfig {

	public static final String[] PUBLIC_ROUTES = {
		"/admin/auth/login", "/admin/auth/mfa/verify", "/admin/auth/mfa/resend", "/admin/auth/password/set"
	};

	@Bean
	@Order(1)
	public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http, AdminProperties properties, AdminTokens tokens,
	                                                    PlatformAdminRepository admins, AdminErrorWriter errors,
	                                                    JwtConfig tenantJwt) throws Exception {
		if (properties.isEnabled()) {
			assertSeparateKeys(properties, tenantJwt);
		}
		http.securityMatcher("/admin/**")
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PUBLIC_ROUTES).permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(e -> e
				.authenticationEntryPoint((req, res, ex) -> errors.write(res, HttpStatus.UNAUTHORIZED, QorvaErrorCodes.AUTH_TOKEN_INVALID))
				.accessDeniedHandler((req, res, ex) -> errors.write(res, HttpStatus.FORBIDDEN, QorvaErrorCodes.ACCESS_FORBIDDEN)))
			.addFilterBefore(new DisabledFilter(properties, errors), UsernamePasswordAuthenticationFilter.class)
			.addFilterBefore(new AdminJwtFilter(tokens, admins), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	/** Two names for one key would let a token of one realm pass for the other. */
	private static void assertSeparateKeys(AdminProperties properties, JwtConfig tenantJwt) {
		if (Arrays.equals(properties.secretKey().getEncoded(), tenantJwt.getSecretKey().getEncoded())) {
			throw new IllegalStateException("ADMIN_JWT_SECRET must differ from JWT_SECRET");
		}
	}

	/** Hides the admin API entirely while it is switched off. */
	static final class DisabledFilter extends OncePerRequestFilter {
		private final AdminProperties properties;
		private final AdminErrorWriter errors;

		DisabledFilter(AdminProperties properties, AdminErrorWriter errors) {
			this.properties = properties;
			this.errors = errors;
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
			if (!properties.isEnabled()) {
				errors.write(response, HttpStatus.NOT_FOUND, QorvaErrorCodes.HTTP_NOT_FOUND);
				return;
			}
			chain.doFilter(request, response);
		}
	}
}
