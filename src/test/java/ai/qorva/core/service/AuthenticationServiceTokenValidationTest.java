package ai.qorva.core.service;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.UserMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * /auth/token/validate must answer every bad token as a failed auth check. Anything that
 * escapes as a raw exception surfaces to the client as a 500 and pollutes error tracking
 * with what is really just an expired session.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTokenValidationTest {

	@Mock private QorvaUserDetailsService userDetailsService;
	@Mock private UserRepository userRepository;
	@Mock private AuthenticationManager authenticationManager;
	@Mock private UserMapper userMapper;
	@Mock private TenantService tenantService;

	private JwtConfig jwtConfig;
	private AuthenticationService service;

	@BeforeEach
	void setUp() {
		// HS512 needs a 64-byte key; any deterministic filler works for a unit test.
		var keyBytes = new byte[64];
		for (int i = 0; i < keyBytes.length; i++) {
			keyBytes[i] = (byte) i;
		}
		jwtConfig = new JwtConfig();
		jwtConfig.setSecret(Base64.getEncoder().encodeToString(keyBytes));
		jwtConfig.setSecretKey(new SecretKeySpec(keyBytes, "HmacSHA512"));
		service = new AuthenticationService(userDetailsService, userRepository, authenticationManager,
			jwtConfig, userMapper, tenantService);
	}

	private String token(long expiresInMillis) {
		return Jwts.builder()
			.subject("recruiter@example.com")
			.issuedAt(new Date(System.currentTimeMillis() - 60_000))
			.expiration(new Date(System.currentTimeMillis() + expiresInMillis))
			.signWith(jwtConfig.getSecretKey())
			.compact();
	}

	@Test
	void aLiveTokenIsValid() throws QorvaException {
		assertThat(service.isTokenValid("Bearer " + token(3_600_000))).isTrue();
	}

	@Test
	void anExpiredTokenIsReportedAsExpiredNotAsAServerError() {
		assertThatThrownBy(() -> service.isTokenValid("Bearer " + token(-30_000)))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.AUTH_TOKEN_EXPIRED);
	}

	@Test
	void aMalformedTokenIsReportedAsInvalidNotAsAServerError() {
		assertThatThrownBy(() -> service.isTokenValid("Bearer not-a-real-token"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.AUTH_TOKEN_INVALID);
	}

	@Test
	void aTokenSignedWithAnotherSecretIsReportedAsInvalid() {
		var foreignKey = new byte[64];
		java.util.Arrays.fill(foreignKey, (byte) 7);
		var forged = Jwts.builder()
			.subject("attacker@example.com")
			.expiration(new Date(System.currentTimeMillis() + 3_600_000))
			.signWith(new SecretKeySpec(foreignKey, "HmacSHA512"))
			.compact();

		assertThatThrownBy(() -> service.isTokenValid("Bearer " + forged))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.AUTH_TOKEN_INVALID);
	}

	@Test
	void aMissingOrNonBearerHeaderIsSimplyNotValid() throws QorvaException {
		assertThat(service.isTokenValid(null)).isFalse();
		assertThat(service.isTokenValid("")).isFalse();
		assertThat(service.isTokenValid("Basic abc")).isFalse();
	}
}
