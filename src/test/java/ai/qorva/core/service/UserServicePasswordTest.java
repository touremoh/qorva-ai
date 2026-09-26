package ai.qorva.core.service;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.querybuilder.UserQueryBuilder;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.UserMapper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServicePasswordTest {

	private static final String TENANT = new ObjectId().toHexString();
	private static final String USER_ID = new ObjectId().toHexString();
	private static final String EMAIL = "ada@a.qorva.test";

	@Mock private UserRepository userRepository;
	@Mock private UserMapper userMapper;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private UserQueryBuilder queryBuilder;
	@Mock private TenantService tenantService;
	@Mock private PendingEmailNotificationService pendingEmailNotificationService;

	private UserService service;

	@BeforeEach
	void setUp() {
		service = new UserService(userRepository, userMapper, passwordEncoder, queryBuilder, tenantService, pendingEmailNotificationService);
	}

	@Test
	void updatePassword_bumpsCredentialVersion_soOutstandingResetLinksDie() throws QorvaException {
		var user = user(2);
		when(userRepository.findByIdInTenant(USER_ID, TENANT)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("old", "$old")).thenReturn(true);
		when(passwordEncoder.encode("new")).thenReturn("$new");

		service.updatePassword(TENANT, USER_ID, EMAIL, "old", "new");

		verify(userRepository).save(user);
		assertThat(user.getEncryptedPassword()).isEqualTo("$new");
		assertThat(user.getPasswordCredentialVersion()).isEqualTo(3);
	}

	@Test
	void updatePassword_legacyUserWithoutVersion_startsAtOne() throws QorvaException {
		var user = user(null);
		when(userRepository.findByIdInTenant(USER_ID, TENANT)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("old", "$old")).thenReturn(true);
		when(passwordEncoder.encode("new")).thenReturn("$new");

		service.updatePassword(TENANT, USER_ID, EMAIL, "old", "new");

		assertThat(user.getPasswordCredentialVersion()).isEqualTo(1);
	}

	@Test
	void updatePassword_wrongCurrentPassword_isRejected() {
		when(userRepository.findByIdInTenant(USER_ID, TENANT)).thenReturn(Optional.of(user(2)));
		when(passwordEncoder.matches("wrong", "$old")).thenReturn(false);

		assertThatThrownBy(() -> service.updatePassword(TENANT, USER_ID, EMAIL, "wrong", "new"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.USER_PASSWORD_INCORRECT);
		verify(userRepository, never()).save(any());
	}

	@Test
	void updatePassword_ofAnotherUser_isRefusedBeforeCheckingAnything() {
		when(userRepository.findByIdInTenant(USER_ID, TENANT)).thenReturn(Optional.of(user(2)));

		assertThatThrownBy(() -> service.updatePassword(TENANT, USER_ID, "someone.else@a.qorva.test", "old", "new"))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.ACCESS_FORBIDDEN);
		verify(passwordEncoder, never()).matches(any(), any());
		verify(userRepository, never()).save(any());
	}

	private static User user(Integer version) {
		var user = new User();
		user.setId(USER_ID);
		user.setTenantId(TENANT);
		user.setEmail(EMAIL);
		user.setEncryptedPassword("$old");
		user.setPasswordCredentialVersion(version);
		return user;
	}
}
