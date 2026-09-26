package ai.qorva.core.service;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.querybuilder.UserQueryBuilder;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.UserMapper;
import ai.qorva.core.security.TenantContextHolder;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

/**
 * The generic update writes the document whose ownership it checked — never a document named by an
 * id smuggled into the payload (which could belong to another tenant).
 */
@ExtendWith(MockitoExtension.class)
class AbstractQorvaServiceUpdateTest {

	private static final String TENANT = new ObjectId().toHexString();
	private static final String OTHER_TENANT = new ObjectId().toHexString();
	private static final String PATH_ID = new ObjectId().toHexString();
	private static final String FOREIGN_ID = new ObjectId().toHexString();

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
		TenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		TenantContextHolder.clear();
	}

	@Test
	void updateOne_payloadIdIsIgnored_theCheckedDocumentIsTheOneSaved() throws QorvaException {
		when(userRepository.findById(new ObjectId(PATH_ID))).thenReturn(Optional.of(user(PATH_ID, TENANT)));
		when(userMapper.map(any(User.class))).thenReturn(new UserDTO());
		var saved = ArgumentCaptor.forClass(UserDTO.class);
		when(userMapper.map(saved.capture())).thenReturn(new User());
		when(userRepository.save(any(User.class))).thenReturn(new User());

		var payload = new UserDTO();
		payload.setId(FOREIGN_ID);
		payload.setTenantId(OTHER_TENANT);
		payload.setFirstName("Ada");

		service.updateOne(PATH_ID, payload);

		assertThat(saved.getValue().getId()).isEqualTo(PATH_ID);
		assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT);
	}

	@Test
	void updateOne_documentOfAnotherTenant_isRejectedBeforeAnyWrite() {
		when(userRepository.findById(new ObjectId(PATH_ID))).thenReturn(Optional.of(user(PATH_ID, OTHER_TENANT)));

		assertThatThrownBy(() -> service.updateOne(PATH_ID, new UserDTO()))
			.isInstanceOf(QorvaException.class);
		verify(userRepository, never()).save(any());
	}

	private static User user(String id, String tenantId) {
		var user = new User();
		user.setId(id);
		user.setTenantId(tenantId);
		return user;
	}
}
