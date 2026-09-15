package ai.qorva.core.service;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.enums.EmailNotificationType;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.utils.JwtUtils;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetPasswordServiceTest {

	private static final String TENANT = new ObjectId().toHexString();
	private static final String USER_ID = new ObjectId().toHexString();
	private static final String EMAIL = "alice@acme.test";
	private static final String APP_BASE_URL = "https://app.qorva.test/";

	@Mock private UserRepository userRepository;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private PendingEmailNotificationService pendingEmailNotificationService;
	@Mock private TenantService tenantService;

	private JwtConfig jwtConfig;
	private SetPasswordService service;

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
		jwtConfig.setPasswordResetTtlInMillis(Duration.ofHours(1).toMillis());
		jwtConfig.setSetPasswordTtlInMillis(Duration.ofHours(72).toMillis());

		service = new SetPasswordService(userRepository, passwordEncoder, jwtConfig, pendingEmailNotificationService, tenantService);
		ReflectionTestUtils.setField(service, "appBaseUrl", APP_BASE_URL);
	}

	// --- requestReset (forgot password) ---

	@Test
	void requestReset_unknownEmail_queuesNothing() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(null);

		service.requestReset(EMAIL);

		verify(pendingEmailNotificationService, never()).createPending(anyString(), anyString(), any(), anyString(), any());
	}

	@Test
	void requestReset_blockedStatuses_queueNothing() {
		for (var status : new UserStatusEnum[]{UserStatusEnum.DELETED, UserStatusEnum.INACTIVE, UserStatusEnum.LOCKED}) {
			when(userRepository.findByEmail(EMAIL)).thenReturn(user(status, 3));

			service.requestReset(EMAIL);
		}

		verify(pendingEmailNotificationService, never()).createPending(anyString(), anyString(), any(), anyString(), any());
	}

	@Test
	void requestReset_insideCooldown_queuesNothing() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(user(UserStatusEnum.ACTIVE, 3));
		when(pendingEmailNotificationService.existsRecent(USER_ID, EmailNotificationType.PASSWORD_RESET, SetPasswordService.RESET_COOLDOWN))
			.thenReturn(true);

		service.requestReset(EMAIL);

		verify(pendingEmailNotificationService, never()).createPending(anyString(), anyString(), any(), anyString(), any());
	}

	@Test
	void requestReset_activeUser_queuesResetEmailWithShortLivedSingleUseLink() throws QorvaException {
		var user = user(UserStatusEnum.ACTIVE, 3);
		user.setCommunicationLanguage("fr");
		when(userRepository.findByEmail(EMAIL)).thenReturn(user);
		when(pendingEmailNotificationService.existsRecent(eq(USER_ID), eq(EmailNotificationType.PASSWORD_RESET), any())).thenReturn(false);
		var tenant = new TenantDTO();
		tenant.setTenantName("Acme");
		when(tenantService.findOneById(TENANT)).thenReturn(tenant);

		long before = System.currentTimeMillis();
		service.requestReset(EMAIL);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
		verify(pendingEmailNotificationService).createPending(eq(TENANT), eq(USER_ID), eq(EmailNotificationType.PASSWORD_RESET), eq("fr"), payload.capture());

		assertThat(payload.getValue()).containsEntry("companyName", "Acme");
		var url = payload.getValue().get("resetPasswordUrl");
		assertThat(url).startsWith("https://app.qorva.test/fr/reset-password?token=");

		var claims = JwtUtils.extractAllClaims(url.substring(url.indexOf("token=") + 6), jwtConfig.getSecretKey());
		assertThat(claims.getSubject()).isEqualTo(USER_ID);
		assertThat(claims.get(JwtUtils.PURPOSE, String.class)).isEqualTo(JwtUtils.PURPOSE_SET_PASSWORD);
		assertThat(claims.get(JwtUtils.CREDENTIAL_VERSION, Integer.class)).isEqualTo(3);
		// 1h reset TTL, not the 72h activation TTL
		assertThat(claims.getExpiration().getTime() - before)
			.isBetween(Duration.ofMinutes(59).toMillis(), Duration.ofMinutes(61).toMillis());
	}

	@Test
	void requestReset_demoUserWithoutVersion_pinsVersionZero() throws QorvaException {
		when(userRepository.findByEmail(EMAIL)).thenReturn(user(UserStatusEnum.DEMO, null));
		when(pendingEmailNotificationService.existsRecent(eq(USER_ID), eq(EmailNotificationType.PASSWORD_RESET), any())).thenReturn(false);
		when(tenantService.findOneById(TENANT)).thenReturn(new TenantDTO());

		service.requestReset(EMAIL);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
		verify(pendingEmailNotificationService).createPending(eq(TENANT), eq(USER_ID), eq(EmailNotificationType.PASSWORD_RESET), eq("en"), payload.capture());
		var url = payload.getValue().get("resetPasswordUrl");
		var claims = JwtUtils.extractAllClaims(url.substring(url.indexOf("token=") + 6), jwtConfig.getSecretKey());
		assertThat(claims.get(JwtUtils.CREDENTIAL_VERSION, Integer.class)).isZero();
	}

	// --- setPassword (consume) ---

	@Test
	void setPassword_happyPath_encodesPasswordAndBumpsVersion() throws QorvaException {
		var user = user(UserStatusEnum.ACTIVE, 3);
		when(userRepository.findById(new ObjectId(USER_ID))).thenReturn(Optional.of(user));
		when(passwordEncoder.encode("N3w-Passw0rd!")).thenReturn("$hash");

		service.setPassword(token(3, Duration.ofHours(1)), "N3w-Passw0rd!");

		verify(userRepository).save(user);
		assertThat(user.getEncryptedPassword()).isEqualTo("$hash");
		assertThat(user.getPasswordCredentialVersion()).isEqualTo(4);
	}

	@Test
	void setPassword_versionMismatch_isRejectedAsUsed() {
		when(userRepository.findById(new ObjectId(USER_ID))).thenReturn(Optional.of(user(UserStatusEnum.ACTIVE, 4)));

		assertThatThrownBy(() -> service.setPassword(token(3, Duration.ofHours(1)), "N3w-Passw0rd!"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.AUTH_SET_PASSWORD_TOKEN_USED);
		verify(userRepository, never()).save(any());
	}

	@Test
	void setPassword_blockedStatus_isRejectedAsInvalid() {
		when(userRepository.findById(new ObjectId(USER_ID))).thenReturn(Optional.of(user(UserStatusEnum.DELETED, 3)));

		assertThatThrownBy(() -> service.setPassword(token(3, Duration.ofHours(1)), "N3w-Passw0rd!"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.AUTH_SET_PASSWORD_TOKEN_INVALID);
		verify(userRepository, never()).save(any());
	}

	@Test
	void setPassword_expiredToken_isRejectedAsInvalid() {
		assertThatThrownBy(() -> service.setPassword(token(3, Duration.ofHours(-1)), "N3w-Passw0rd!"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.AUTH_SET_PASSWORD_TOKEN_INVALID);
		verify(userRepository, never()).save(any());
	}

	@Test
	void setPassword_accessTokenWithoutPurpose_isRejectedAsInvalid() {
		var tenant = new TenantDTO();
		tenant.setId(TENANT);
		var principal = org.springframework.security.core.userdetails.User.withUsername(EMAIL).password("x").authorities("ROLE").build();
		var accessToken = JwtUtils.generateToken(principal, jwtConfig, tenant);

		assertThatThrownBy(() -> service.setPassword(accessToken, "N3w-Passw0rd!"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.AUTH_SET_PASSWORD_TOKEN_INVALID);
	}

	// --- helpers ---

	private static User user(UserStatusEnum status, Integer version) {
		var user = new User();
		user.setId(USER_ID);
		user.setTenantId(TENANT);
		user.setEmail(EMAIL);
		user.setFirstName("Alice");
		user.setUserAccountStatus(status.getValue());
		user.setPasswordCredentialVersion(version);
		return user;
	}

	private String token(int version, Duration ttl) {
		return JwtUtils.generateSetPasswordToken(USER_ID, version, jwtConfig, ttl.toMillis());
	}
}
