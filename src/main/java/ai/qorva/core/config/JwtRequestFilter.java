package ai.qorva.core.config;

import ai.qorva.core.service.ApplicationUserDetailsService;
import ai.qorva.core.utils.JwtUtils;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

@Configuration
public class JwtRequestFilter extends OncePerRequestFilter {

	private final JwtConfig jwtConfig;
	private final ApplicationUserDetailsService userDetailsService;

	@Autowired
	public JwtRequestFilter(ApplicationUserDetailsService userDetailsService, JwtConfig jwtConfig) {
		this.userDetailsService = userDetailsService;
		this.jwtConfig = jwtConfig;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		final String authorizationHeader = request.getHeader("Authorization");

		String email = null;
		String jwt = null;

		if (Objects.nonNull(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
			jwt = authorizationHeader.substring(7);
			try {
				email = JwtUtils.extractUsername(jwt, jwtConfig.getSecretKey());
			} catch (ExpiredJwtException e) {
				response.setHeader("Error during authentication", "Token has expired");
				chain.doFilter(request, response);
				return;
			}
		}

		if (Objects.nonNull(email) && Objects.isNull(SecurityContextHolder.getContext().getAuthentication())) {
			UserDetails userDetails = this.userDetailsService.loadUserByUsername(email);
			if (Boolean.TRUE.equals(JwtUtils.isTokenValid(jwt, userDetails, jwtConfig.getSecretKey()))) {
				UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
					userDetails,
					null,
					userDetails.getAuthorities()
				);
				authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authenticationToken);
			}
		}
		chain.doFilter(request, response);
	}
}
