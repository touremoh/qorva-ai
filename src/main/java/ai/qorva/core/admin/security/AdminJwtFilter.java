package ai.qorva.core.admin.security;

import ai.qorva.core.dao.entity.PlatformAdmin;
import ai.qorva.core.dao.repository.PlatformAdminRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates {@code /admin/**} requests from an admin token. The admin is reloaded on every request, so a
 * disabled admin, a role change or a password change (all bump {@code credentialVersion}) end the session at once.
 * Never sets a tenant: admin work enters a tenant explicitly through {@code TenantScope}.
 * Not a Spring bean on purpose — as one, Boot would also register it for every route.
 */
@Slf4j
public class AdminJwtFilter extends OncePerRequestFilter {

	public static final String ROLE_AUTHORITY_PREFIX = "ADMIN_ROLE:";

	private final AdminTokens tokens;
	private final PlatformAdminRepository admins;

	public AdminJwtFilter(AdminTokens tokens, PlatformAdminRepository admins) {
		this.tokens = tokens;
		this.admins = admins;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		var header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			tokens.parse(header.substring(7), AdminTokens.TYPE_ADMIN)
				.flatMap(claims -> admins.findById(claims.getSubject())
					.filter(admin -> PlatformAdmin.STATUS_ACTIVE.equals(admin.getStatus()))
					.filter(admin -> admin.getCredentialVersion() == AdminTokens.credentialVersion(claims)))
				.ifPresent(admin -> {
					var principal = new AdminPrincipal(admin.getId(), admin.getEmail(), admin.getRole());
					var auth = new UsernamePasswordAuthenticationToken(principal, null,
						List.of(new SimpleGrantedAuthority(ROLE_AUTHORITY_PREFIX + admin.getRole())));
					SecurityContextHolder.getContext().setAuthentication(auth);
				});
		}
		chain.doFilter(request, response);
	}
}
