package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.service.DemoDataPurgeService;
import ai.qorva.core.service.PendingEmailNotificationService;
import ai.qorva.core.service.TenantService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A completed checkout only activates (and, for a demo account, purges) the tenant the user belongs to. */
@ExtendWith(MockitoExtension.class)
class StripeCheckoutSessionCompletedHandlerActivationTest {

	private static final String TENANT = new ObjectId().toHexString();
	private static final String OTHER_TENANT = new ObjectId().toHexString();
	private static final String USER_ID = new ObjectId().toHexString();

	@Mock private TenantService tenantService;
	@Mock private StripeEventLogRepository repository;
	@Mock private StripeEventMapper evtMapper;
	@Mock private UserRepository userRepository;
	@Mock private PendingEmailNotificationService pendingEmailService;
	@Mock private DemoDataPurgeService demoDataPurgeService;

	private StripeCheckoutSessionCompletedHandler handler;

	@BeforeEach
	void setUp() {
		handler = new StripeCheckoutSessionCompletedHandler(tenantService, repository, evtMapper, userRepository,
			pendingEmailService, demoDataPurgeService);
	}

	@Test
	void demoUserOfTheSessionTenant_isActivatedAndItsTenantPurged() {
		var user = demoUser(TENANT);
		when(userRepository.findById(new ObjectId(USER_ID))).thenReturn(Optional.of(user));

		var activated = handler.activateUser(TENANT, USER_ID, null);

		assertThat(activated).contains(user);
		assertThat(user.getUserAccountStatus()).isEqualTo(UserStatusEnum.ACTIVE.getValue());
		verify(demoDataPurgeService).purgeAll(TENANT);
	}

	@Test
	void demoUserOfAnotherTenant_neverTriggersActivationOrPurge() {
		when(userRepository.findById(new ObjectId(USER_ID))).thenReturn(Optional.of(demoUser(OTHER_TENANT)));

		var activated = handler.activateUser(TENANT, USER_ID, null);

		assertThat(activated).isEmpty();
		verify(demoDataPurgeService, never()).purgeAll(anyString());
		verify(userRepository, never()).save(any());
	}

	@Test
	void missingTenantReference_neverTriggersActivationOrPurge() {
		when(userRepository.findById(new ObjectId(USER_ID))).thenReturn(Optional.of(demoUser(TENANT)));

		assertThat(handler.activateUser(null, USER_ID, null)).isEmpty();
		verify(demoDataPurgeService, never()).purgeAll(any());
	}

	private static User demoUser(String tenantId) {
		var user = new User();
		user.setId(USER_ID);
		user.setTenantId(tenantId);
		user.setUserAccountStatus(UserStatusEnum.DEMO.getValue());
		return user;
	}
}
