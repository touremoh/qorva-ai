package ai.qorva.core.service;

import ai.qorva.core.config.StripeProperties;
import ai.qorva.core.config.QorvaProductProperties;
import ai.qorva.core.dto.CheckoutSessionRequestDTO;
import ai.qorva.core.dto.ProductReferenceDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AccountRegistrationMapper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The public checkout-session route only acts on a user/tenant pair that really belongs together. */
@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceCheckoutTest {

	private static final String TENANT = new ObjectId().toHexString();
	private static final String OTHER_TENANT = new ObjectId().toHexString();
	private static final String USER_ID = new ObjectId().toHexString();

	@Mock private UserService userService;
	@Mock private TenantService tenantService;
	@Mock private ProductReferenceService productReferenceService;
	@Mock private AccountRegistrationMapper accountRegistrationMapper;
	@Mock private StripeProperties stripeProperties;
	@Mock private SetPasswordService setPasswordService;
	@Mock private DemoSeedService demoSeedService;
	@Mock private UsageMonitoringService usageMonitoringService;
	@Mock private QorvaProductProperties qorvaProductProperties;

	private UserRegistrationService service;

	@BeforeEach
	void setUp() throws QorvaException {
		service = new UserRegistrationService(userService, tenantService, productReferenceService, accountRegistrationMapper,
			stripeProperties, setPasswordService, demoSeedService, usageMonitoringService, qorvaProductProperties);
		when(productReferenceService.findByStripePriceId("price_1")).thenReturn(new ProductReferenceDTO());
	}

	@Test
	void userOfAnotherTenant_isRefusedBeforeTheTenantIsTouched() throws QorvaException {
		var user = new UserDTO();
		user.setTenantId(OTHER_TENANT);
		when(userService.findOneById(USER_ID)).thenReturn(user);

		assertNotFound(request(TENANT, USER_ID));
		verifyNoInteractions(tenantService);
	}

	@Test
	void malformedIds_areRefused() {
		assertNotFound(request("not-an-id", USER_ID));
		assertNotFound(request(TENANT, "not-an-id"));
		verifyNoInteractions(tenantService);
	}

	private void assertNotFound(CheckoutSessionRequestDTO request) {
		assertThatThrownBy(() -> service.renewCheckoutSession(request))
			.isInstanceOfSatisfying(QorvaException.class,
				e -> assertThat(e.getHttpStatusCode()).isEqualTo(HttpStatus.NOT_FOUND.value()));
	}

	private static CheckoutSessionRequestDTO request(String tenantId, String userId) {
		var dto = new CheckoutSessionRequestDTO();
		dto.setTenantId(tenantId);
		dto.setUserId(userId);
		dto.setPriceId("price_1");
		return dto;
	}
}
