package ai.qorva.core.service;

import ai.qorva.core.config.MfaProperties;
import ai.qorva.core.dao.entity.MfaChallenge;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.MfaChallengeRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.enums.MfaPurpose;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import com.mongodb.client.result.UpdateResult;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

	private static final String TENANT = new ObjectId().toHexString();
	private static final String USER_ID = new ObjectId().toHexString();
	private static final String EMAIL = "alice@acme.test";
	private static final String CHALLENGE_ID = "challenge-123";

	@Mock private MfaChallengeRepository challengeRepository;
	@Mock private UserRepository userRepository;
	@Mock private MongoTemplate mongoTemplate;
	@Mock private ObjectProvider<MfaNotificationService> notificationProvider;
	@Mock private MfaNotificationService notifier;

	// Real BCrypt: the tests check that the emailed code matches the stored hash.
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private MfaProperties properties;
	private MfaService service;

	@BeforeEach
	void setUp() {
		properties = new MfaProperties();
		service = new MfaService(challengeRepository, userRepository, passwordEncoder, mongoTemplate, properties, notificationProvider);
	}

	private User user(boolean mfaEnabled) {
		var user = new User();
		user.setId(USER_ID);
		user.setTenantId(TENANT);
		user.setEmail(EMAIL);
		user.setFirstName("Alice");
		user.setUserAccountStatus(UserStatusEnum.ACTIVE.getValue());
		user.setMfaEnabled(mfaEnabled);
		return user;
	}

	private MfaChallenge challenge(MfaPurpose purpose, String code, int attempts) {
		return MfaChallenge.builder()
			.id(CHALLENGE_ID)
			.tenantId(TENANT)
			.userId(USER_ID)
			.purpose(purpose.name())
			.codeHash(passwordEncoder.encode(code))
			.attempts(attempts)
			.sends(1)
			.lastSentAt(Instant.now().minus(Duration.ofMinutes(2)))
			.expiresAt(Instant.now().plus(Duration.ofMinutes(8)))
			.createdAt(Instant.now().minus(Duration.ofMinutes(2)))
			.build();
	}

	private void givenStored(MfaChallenge challenge) {
		when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
	}

	private void givenUser(User user) {
		when(userRepository.findById(new ObjectId(USER_ID))).thenReturn(Optional.of(user));
	}

	/** The attempt reservation returns the challenge with its counter already bumped. */
	private void givenReservation(MfaChallenge challenge, int attemptsAfter) {
		var reserved = MfaChallenge.builder()
			.id(challenge.getId()).userId(challenge.getUserId()).purpose(challenge.getPurpose())
			.codeHash(challenge.getCodeHash()).attempts(attemptsAfter).build();
		when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(MfaChallenge.class)))
			.thenReturn(reserved);
	}

	private static QorvaException caught(ThrowingCall call) {
		try {
			call.run();
		} catch (QorvaException e) {
			return e;
		}
		throw new AssertionError("expected QorvaException");
	}

	@FunctionalInterface
	private interface ThrowingCall {
		void run() throws QorvaException;
	}

	// --- issue ---

	@Test
	void issueLogin_storesOnlyAHashAndEmailsASixDigitCode() throws QorvaException {
		when(notificationProvider.getIfAvailable()).thenReturn(notifier);
		var user = user(true);

		var view = service.issueLogin(user);

		var saved = ArgumentCaptor.forClass(MfaChallenge.class);
		verify(challengeRepository).save(saved.capture());
		var code = ArgumentCaptor.forClass(String.class);
		verify(notifier).sendCode(eq(user), code.capture(), eq(MfaPurpose.LOGIN), eq(10L));

		assertThat(code.getValue()).matches("\\d{6}");
		assertThat(saved.getValue().getCodeHash()).isNotEqualTo(code.getValue());
		assertThat(passwordEncoder.matches(code.getValue(), saved.getValue().getCodeHash())).isTrue();
		assertThat(saved.getValue().getPurpose()).isEqualTo("LOGIN");
		assertThat(saved.getValue().getId()).hasSizeGreaterThanOrEqualTo(43);
		assertThat(view.challengeId()).isEqualTo(saved.getValue().getId());
		assertThat(view.maskedEmail()).isEqualTo("a•••@acme.test");
		assertThat(view.resendAvailableAt()).isAfter(Instant.now().plusSeconds(50));
	}

	@Test
	void issue_tooManyChallengesInWindow_refusesWithoutSending() {
		when(challengeRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(10L);

		var e = caught(() -> service.issueLogin(user(true)));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_TOO_MANY_CODES);
		assertThat(e.getHttpStatusCode()).isEqualTo(429);
		verify(challengeRepository, never()).save(any());
	}

	@Test
	void issue_notificationsDisabled_failsClosedAndDeletesTheChallenge() {
		when(notificationProvider.getIfAvailable()).thenReturn(null);

		var e = caught(() -> service.issueLogin(user(true)));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_DELIVERY_FAILED);
		assertThat(e.getHttpStatusCode()).isEqualTo(503);
		verify(challengeRepository).deleteById(anyString());
	}

	@Test
	void issue_mailFailure_failsClosedAndDeletesTheChallenge() throws QorvaException {
		when(notificationProvider.getIfAvailable()).thenReturn(notifier);
		doThrow(new QorvaException("graph down")).when(notifier).sendCode(any(), anyString(), any(), anyLong());

		var e = caught(() -> service.issueLogin(user(true)));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_DELIVERY_FAILED);
		verify(challengeRepository).deleteById(anyString());
	}

	// --- verify ---

	@Test
	void verifyLogin_rightCode_consumesAndReturnsTheUser() throws QorvaException {
		var stored = challenge(MfaPurpose.LOGIN, "123456", 0);
		givenStored(stored);
		givenUser(user(true));
		givenReservation(stored, 1);
		when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(MfaChallenge.class)))
			.thenReturn(UpdateResult.acknowledged(1, 1L, null));

		var result = service.verifyLogin(CHALLENGE_ID, " 123 456 ");

		assertThat(result.getId()).isEqualTo(USER_ID);
	}

	@Test
	void verifyLogin_rightCodeButAlreadyConsumedConcurrently_isRejected() {
		var stored = challenge(MfaPurpose.LOGIN, "123456", 0);
		givenStored(stored);
		givenUser(user(true));
		givenReservation(stored, 1);
		when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(MfaChallenge.class)))
			.thenReturn(UpdateResult.acknowledged(0, 0L, null));

		var e = caught(() -> service.verifyLogin(CHALLENGE_ID, "123456"));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_CHALLENGE_INVALID);
	}

	@Test
	void verifyLogin_wrongCode_reportsRemainingAttempts() {
		var stored = challenge(MfaPurpose.LOGIN, "123456", 0);
		givenStored(stored);
		givenUser(user(true));
		givenReservation(stored, 1);

		var e = caught(() -> service.verifyLogin(CHALLENGE_ID, "654321"));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_CODE_INVALID);
		assertThat(e.getHttpStatusCode()).isEqualTo(401);
		assertThat(e.getParams()).containsExactly(4);
	}

	@Test
	void verifyLogin_lastWrongCode_burnsTheChallenge() {
		var stored = challenge(MfaPurpose.LOGIN, "123456", 4);
		givenStored(stored);
		givenUser(user(true));
		givenReservation(stored, 5);

		var e = caught(() -> service.verifyLogin(CHALLENGE_ID, "000000"));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_TOO_MANY_ATTEMPTS);
		assertThat(e.getHttpStatusCode()).isEqualTo(429);
		verify(mongoTemplate).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(MfaChallenge.class));
	}

	@Test
	void verifyLogin_noAttemptLeftToReserve_isRejectedEvenWithTheRightCode() {
		var stored = challenge(MfaPurpose.LOGIN, "123456", 4);
		givenStored(stored);
		givenUser(user(true));
		when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(MfaChallenge.class)))
			.thenReturn(null);

		var e = caught(() -> service.verifyLogin(CHALLENGE_ID, "123456"));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_CHALLENGE_INVALID);
	}

	@Test
	void verifyLogin_nonNumericCode_countsAsWrong() {
		var stored = challenge(MfaPurpose.LOGIN, "123456", 0);
		givenStored(stored);
		givenUser(user(true));
		givenReservation(stored, 1);

		var e = caught(() -> service.verifyLogin(CHALLENGE_ID, "12345a"));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_CODE_INVALID);
	}

	@Test
	void verifyLogin_challengeForAnotherPurpose_cannotSignIn() {
		givenStored(challenge(MfaPurpose.ENABLE, "123456", 0));

		var e = caught(() -> service.verifyLogin(CHALLENGE_ID, "123456"));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_CHALLENGE_INVALID);
		verify(mongoTemplate, never()).findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(MfaChallenge.class));
	}

	@Test
	void verifyLogin_expiredChallenge_isRejected() {
		var stored = challenge(MfaPurpose.LOGIN, "123456", 0);
		stored.setExpiresAt(Instant.now().minusSeconds(1));
		givenStored(stored);

		var e = caught(() -> service.verifyLogin(CHALLENGE_ID, "123456"));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_CHALLENGE_INVALID);
	}

	@Test
	void verifyLogin_consumedChallenge_isRejected() {
		var stored = challenge(MfaPurpose.LOGIN, "123456", 1);
		stored.setConsumedAt(Instant.now());
		givenStored(stored);

		var e = caught(() -> service.verifyLogin(CHALLENGE_ID, "123456"));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_CHALLENGE_INVALID);
	}

	@Test
	void verifyLogin_unknownChallenge_isRejected() {
		when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.empty());

		var e = caught(() -> service.verifyLogin(CHALLENGE_ID, "123456"));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_CHALLENGE_INVALID);
	}

	@Test
	void verifyLogin_accountBlockedSinceTheCodeWasSent_isRejected() {
		givenStored(challenge(MfaPurpose.LOGIN, "123456", 0));
		var blocked = user(true);
		blocked.setUserAccountStatus(UserStatusEnum.LOCKED.getValue());
		givenUser(blocked);

		var e = caught(() -> service.verifyLogin(CHALLENGE_ID, "123456"));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_CHALLENGE_INVALID);
	}

	// --- resend ---

	@Test
	void resendLogin_insideCooldown_isRefused() {
		var stored = challenge(MfaPurpose.LOGIN, "123456", 0);
		stored.setLastSentAt(Instant.now().minusSeconds(10));
		givenStored(stored);
		givenUser(user(true));

		var e = caught(() -> service.resendLogin(CHALLENGE_ID));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_RESEND_TOO_SOON);
		assertThat(e.getHttpStatusCode()).isEqualTo(429);
	}

	@Test
	void resendLogin_sendCapReached_isRefused() {
		var stored = challenge(MfaPurpose.LOGIN, "123456", 0);
		stored.setSends(5);
		givenStored(stored);
		givenUser(user(true));

		var e = caught(() -> service.resendLogin(CHALLENGE_ID));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_TOO_MANY_CODES);
	}

	@Test
	void resendLogin_replacesTheCodeAndEmailsIt() throws QorvaException {
		var stored = challenge(MfaPurpose.LOGIN, "123456", 0);
		givenStored(stored);
		var user = user(true);
		givenUser(user);
		when(notificationProvider.getIfAvailable()).thenReturn(notifier);
		var updated = challenge(MfaPurpose.LOGIN, "999999", 0);
		updated.setSends(2);
		updated.setLastSentAt(Instant.now());
		when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(MfaChallenge.class)))
			.thenReturn(updated);

		var view = service.resendLogin(CHALLENGE_ID);

		verify(notifier).sendCode(eq(user), anyString(), eq(MfaPurpose.LOGIN), eq(10L));
		assertThat(view.resendAvailableAt()).isAfter(Instant.now().plusSeconds(50));
	}

	// --- settings ---

	@Test
	void confirmChange_enable_switchesMfaOnAfterAValidCode() throws QorvaException {
		var user = user(false);
		when(userRepository.findByEmail(EMAIL)).thenReturn(user);
		var stored = challenge(MfaPurpose.ENABLE, "123456", 0);
		givenStored(stored);
		givenUser(user);
		givenReservation(stored, 1);
		when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(MfaChallenge.class)))
			.thenReturn(UpdateResult.acknowledged(1, 1L, null));

		var status = service.confirmChange(TENANT, EMAIL, true, CHALLENGE_ID, "123456");

		assertThat(status.enabled()).isTrue();
		assertThat(user.getMfaEnabled()).isTrue();
		assertThat(user.getMfaEnabledAt()).isNotNull();
		verify(userRepository).save(user);
	}

	@Test
	void confirmChange_disableWithAnEnableChallenge_isRejected() {
		var user = user(true);
		when(userRepository.findByEmail(EMAIL)).thenReturn(user);
		givenStored(challenge(MfaPurpose.ENABLE, "123456", 0));

		var e = caught(() -> service.confirmChange(TENANT, EMAIL, false, CHALLENGE_ID, "123456"));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_CHALLENGE_INVALID);
		verify(userRepository, never()).save(any());
	}

	@Test
	void confirmChange_someoneElsesChallenge_isRejected() {
		var caller = user(false);
		caller.setId(new ObjectId().toHexString());
		when(userRepository.findByEmail(EMAIL)).thenReturn(caller);
		givenStored(challenge(MfaPurpose.ENABLE, "123456", 0));

		var e = caught(() -> service.confirmChange(TENANT, EMAIL, true, CHALLENGE_ID, "123456"));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.AUTH_MFA_CHALLENGE_INVALID);
	}

	@Test
	void startChange_enableWhenAlreadyOn_conflicts() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(user(true));

		var e = caught(() -> service.startChange(TENANT, EMAIL, true));

		assertThat(e.getMessage()).isEqualTo(QorvaErrorCodes.MFA_ALREADY_ENABLED);
		assertThat(e.getHttpStatusCode()).isEqualTo(409);
	}

	@Test
	void startChange_otherTenant_isNotFound() {
		var user = user(false);
		user.setTenantId(new ObjectId().toHexString());
		when(userRepository.findByEmail(EMAIL)).thenReturn(user);

		assertThatThrownBy(() -> service.startChange(TENANT, EMAIL, true))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.USER_NOT_FOUND);
	}

	@Test
	void maskEmail_keepsFirstLetterAndDomain() {
		assertThat(MfaService.maskEmail("jane.doe@acme.com")).isEqualTo("j•••@acme.com");
		assertThat(MfaService.maskEmail("broken")).isEqualTo("•••");
		assertThat(MfaService.maskEmail(null)).isEmpty();
	}
}
