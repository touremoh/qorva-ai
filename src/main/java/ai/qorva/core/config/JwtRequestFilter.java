package ai.qorva.core.config;

import ai.qorva.core.enums.SubscriptionStatus;
import ai.qorva.core.security.LanguageContextHolder;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.QorvaUserDetailsService;
import ai.qorva.core.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.lang.Strings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Configuration
public class JwtRequestFilter extends OncePerRequestFilter {

	protected static final String TENANT_ID = "tenantId";
	protected static final String SUBSCRIPTION_PLAN = "subscriptionPlan";
	protected static final String SUBSCRIPTION_STATUS = "subscriptionStatus";

	/** Subscription statuses that block access to the application. */
	private static final Set<String> BLOCKED_STATUSES = Set.of(
		SubscriptionStatus.CANCELED.getValue(),
		SubscriptionStatus.PAST_DUE.getValue()
	);

	/** URI prefixes that bypass subscription enforcement (auth, webhook, public portal). */
	private static final List<String> SUBSCRIPTION_EXEMPT_PREFIXES = List.of(
		"/registrations", "/auth/", "/stripe/webhook", "/actuator", "/portal/jobs"
	);

	private final JwtConfig jwtConfig;
	private final QorvaUserDetailsService userDetailsService;

	@Autowired
	public JwtRequestFilter(QorvaUserDetailsService userDetailsService, JwtConfig jwtConfig) {
		this.userDetailsService = userDetailsService;
		this.jwtConfig = jwtConfig;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		try {
			String authorizationHeader = request.getHeader("Authorization");

			if (Strings.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
				String token = authorizationHeader.substring(7);
				if (Strings.hasText(token) && !token.equals("null")) {
					Claims claims = JwtUtils.extractAllClaims(token, jwtConfig.getSecretKey());

					String username = claims.getSubject();
					String tenantId = claims.get(TENANT_ID, String.class);

					if (Strings.hasText(username) && SecurityContextHolder.getContext().getAuthentication() == null) {
						UserDetails userDetails = userDetailsService.loadUserByUsername(username);

						if (Boolean.TRUE.equals(JwtUtils.isTokenValid(token, userDetails, jwtConfig.getSecretKey()))) {
							// Merge subscription-level authorities (from JWT) with action-level authorities (from UserDetails)
							List<GrantedAuthority> authorities = new ArrayList<>(userDetails.getAuthorities());

							var subscriptionPlan = claims.get(SUBSCRIPTION_PLAN, String.class);
							var subscriptionStatus = claims.get(SUBSCRIPTION_STATUS, String.class);
							if (Strings.hasText(subscriptionPlan)) {
								authorities.add(new SimpleGrantedAuthority(subscriptionPlan));
								authorities.add(new SimpleGrantedAuthority(subscriptionStatus));
							}

							var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
							authentication.setDetails(tenantId);
							SecurityContextHolder.getContext().setAuthentication(authentication);
							TenantContextHolder.setTenantId(tenantId);

							// Subscription enforcement: block cancelled/expired tenants on non-exempt paths
							if (isSubscriptionBlocked(subscriptionStatus, request.getRequestURI())) {
								response.setStatus(HttpServletResponse.SC_PAYMENT_REQUIRED);
								response.setContentType(MediaType.APPLICATION_JSON_VALUE);
								response.getWriter().write("{\"error\":\"Your subscription is not active. Please renew your plan.\"}");
								return;
							}
						}
					}
				}
			}
			chain.doFilter(request, response);
		} finally {
			TenantContextHolder.clear();
			LanguageContextHolder.clear();
		}
	}

	private boolean isSubscriptionBlocked(String subscriptionStatus, String requestUri) {
		if (!Strings.hasText(subscriptionStatus) || !BLOCKED_STATUSES.contains(subscriptionStatus)) {
			return false;
		}
		return SUBSCRIPTION_EXEMPT_PREFIXES.stream().noneMatch(requestUri::startsWith);
	}
}
