package ai.qorva.core.config;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Configuration
public class JwtRequestFilter extends OncePerRequestFilter {

	protected static final String TENANT_ID = "tenantId";
	protected static final String SUBSCRIPTION_PLAN = "subscriptionPlan";
	protected static final String SUBSCRIPTION_STATUS = "subscriptionStatus";

	private final JwtConfig jwtConfig;
	private final QorvaUserDetailsService userDetailsService;

	@Autowired
	public JwtRequestFilter(QorvaUserDetailsService userDetailsService, JwtConfig jwtConfig) {
		this.userDetailsService = userDetailsService;
		this.jwtConfig = jwtConfig;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		String authorizationHeader = request.getHeader("Authorization");

		if (Strings.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
			String token = authorizationHeader.substring(7);
			if (Strings.hasText(token) && !token.equals("null")) {
				Claims claims = JwtUtils.extractAllClaims(token, jwtConfig.getSecretKey());

				String username = claims.getSubject();
				String tenantId = claims.get(TENANT_ID, String.class);

				// then create UsernamePasswordAuthenticationToken with these authorities
				if (Strings.hasText(username) && SecurityContextHolder.getContext().getAuthentication() == null) {
					UserDetails userDetails = userDetailsService.loadUserByUsername(username);

					// Set the authorities for the user
					var authorities = new ArrayList<GrantedAuthority>();
					var subscriptionPlan = claims.get(SUBSCRIPTION_PLAN, String.class);
					var subscriptionStatus = claims.get(SUBSCRIPTION_STATUS, String.class);

					if (Strings.hasText(subscriptionPlan)) {
						authorities.add(new SimpleGrantedAuthority(subscriptionPlan));
						authorities.add(new SimpleGrantedAuthority(subscriptionStatus));
					}

					if (Boolean.TRUE.equals(JwtUtils.isTokenValid(token, userDetails, jwtConfig.getSecretKey()))) {
						var authentication = new UsernamePasswordAuthenticationToken(
							userDetails,
							null,
							authorities
						);

						// Include tenantId in the authentication details
						authentication.setDetails(tenantId);
						SecurityContextHolder.getContext().setAuthentication(authentication);
					}
				}
			}
		}
		chain.doFilter(request, response);
	}
}
