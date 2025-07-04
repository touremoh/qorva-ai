package ai.qorva.core.config;

import ai.qorva.core.service.QorvaUserDetailsService;
import ai.qorva.core.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
public class JwtRequestFilter extends OncePerRequestFilter {

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

		if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
			String token = authorizationHeader.substring(7); // Remove "Bearer " prefix
			Claims claims = JwtUtils.extractAllClaims(token, jwtConfig.getSecretKey());

			String username = claims.getSubject();
			String tenantId = claims.get("tenantId", String.class);

			if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
				UserDetails userDetails = userDetailsService.loadUserByUsername(username);

				if (Boolean.TRUE.equals(JwtUtils.isTokenValid(token, userDetails, jwtConfig.getSecretKey()))) {
					var authentication = new UsernamePasswordAuthenticationToken(
						userDetails,
						null,
						userDetails.getAuthorities()
					);

					// Include tenantId in the authentication details
					authentication.setDetails(tenantId);
					SecurityContextHolder.getContext().setAuthentication(authentication);
				}
			}
		}
		chain.doFilter(request, response);
	}
}
