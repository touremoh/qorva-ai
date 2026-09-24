package ai.qorva.core.service;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.MfaData;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.UserMapper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User.UserBuilder;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceMfaTest {

	private static final String EMAIL = "recruiter@example.com";
	private static final String TENANT = new ObjectId().toHexString();

	@Mock private QorvaUserDetailsService userDetailsService;
	@Mock private UserRepository userRepository;
	@Mock private AuthenticationManager authenticationManager;
	@Mock private UserMapper userMapper;
	@Mock private TenantService tenantService;
	@Mock private MfaService mfaService;

	private AuthenticationService service;

	@BeforeEach
	void setUp() {
		var keyBytes = new byte[64];
		for (int i = 0; i < keyBytes.length; i++) {
			keyBytes[i] = (byte) i;
		}
		var jwtConfig = new JwtConfig();
		jwtConfig.setSecret(Base64.getEncoder().encodeToString(keyBytes));
		jwtConfig.setSecretKey(new SecretKeySpec(keyBytes, "HmacSHA512"));
		service = new AuthenticationService(userDetailsService, userRepository, authenticationManager,
			jwtConfig, userMapper, tenantService, mfaService);
	}

	private UserDTO credentials() {
		var dto = new UserDTO();
		dto.setEmail(EMAIL);
		dto.setRawPassword("secret");
		return dto;
	}

	private User user(Boolean mfaEnabled) {
		var user = new User();
		user.setId(new ObjectId().toHexString());
		user.setTenantId(TENANT);
		user.setEmail(EMAIL);
		user.setMfaEnabled(mfaEnabled);
		return user;
	}

	private void givenSignInSucceeds(User user) throws QorvaException {
		UserBuilder builder = org.springframework.security.core.userdetails.User.withUsername(EMAIL);
		UserDetails details = builder.password("x").authorities("VIEW_CV").build();
		when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(details);
		var tenant = new TenantDTO();
		tenant.setId(TENANT);
		when(tenantService.findOneById(TENANT)).thenReturn(tenant);
		when(userMapper.map(user)).thenReturn(new UserDTO());
	}

	@Test
	void mfaOff_legacyUserWithoutFlag_signsInAsBefore() throws QorvaException {
		var user = user(null);
		when(userRepository.findByEmail(EMAIL)).thenReturn(user);
		givenSignInSucceeds(user);

		var response = service.authenticate(credentials());

		assertThat(response.jwt()).isNotNull();
		assertThat(response.jwt().getAccessToken()).isNotBlank();
		assertThat(response.user()).isNotNull();
		assertThat(response.mfa()).isNull();
		verifyNoInteractions(mfaService);
	}

	@Test
	void mfaOn_returnsAChallengeAndNoToken() throws QorvaException {
		var user = user(true);
		when(userRepository.findByEmail(EMAIL)).thenReturn(user);
		var challenge = new MfaData.Challenge("c1", "r•••@example.com", Instant.now(), Instant.now());
		when(mfaService.issueLogin(user)).thenReturn(challenge);

		var response = service.authenticate(credentials());

		assertThat(response.jwt()).isNull();
		assertThat(response.user()).isNull();
		assertThat(response.mfa()).isEqualTo(challenge);
		verify(userDetailsService, never()).loadUserByUsername(anyString());
	}

	@Test
	void wrongPassword_withMfaOn_failsExactlyAsBeforeAndSendsNothing() {
		when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

		assertThatThrownBy(() -> service.authenticate(credentials()))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.AUTH_FAILED);
		verifyNoInteractions(mfaService);
	}

	@Test
	void mfaDeliveryFailure_isNotMaskedAsBadCredentials() throws QorvaException {
		var user = user(true);
		when(userRepository.findByEmail(EMAIL)).thenReturn(user);
		when(mfaService.issueLogin(user)).thenThrow(new QorvaException(QorvaErrorCodes.AUTH_MFA_DELIVERY_FAILED, 503,
			org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(() -> service.authenticate(credentials()))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.AUTH_MFA_DELIVERY_FAILED);
	}

	@Test
	void verifyMfa_validCode_mintsTheUsualToken() throws QorvaException {
		var user = user(true);
		when(mfaService.verifyLogin("c1", "123456")).thenReturn(user);
		givenSignInSucceeds(user);

		var response = service.verifyMfa("c1", "123456");

		assertThat(response.jwt().getAccessToken()).isNotBlank();
		assertThat(response.mfa()).isNull();
	}
}
