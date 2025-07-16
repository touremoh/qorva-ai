package ai.qorva.core.service;

import ai.qorva.core.dto.*;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.enums.UserAccountStatus;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AccountRegistrationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class UserRegistrationService {
	private final UserService userService;
	private final TenantService tenantService;
	private final StripeEventsService stripeEventsService;
	private final AccountRegistrationMapper accountRegistrationMapper;
	private final AccountCreationNotificationService accountCreationNotificationService;

	@Autowired
	public UserRegistrationService(
		UserService userService,
		TenantService tenantService, StripeEventsService stripeEventsService,
		AccountRegistrationMapper accountRegistrationMapper,
		AccountCreationNotificationService accountCreationNotificationService
	) {
		this.userService = userService;
		this.tenantService = tenantService;
		this.stripeEventsService = stripeEventsService;
		this.accountRegistrationMapper = accountRegistrationMapper;
		this.accountCreationNotificationService = accountCreationNotificationService;
	}

	@Transactional
	public UserDTO createAccount(AccountRegistrationDTO newAccountDTO, String languageCode) throws QorvaException {
		// Set language code
		newAccountDTO.setLanguageCode(languageCode);

		// Build user info
		var userDTO = this.accountRegistrationMapper.map(newAccountDTO);

		// Get the organization name (or take the name of user)
		final String companyName = StringUtils.hasText(newAccountDTO.getCompanyName())
			? newAccountDTO.getCompanyName()
			: newAccountDTO.getFirstName() + " " + newAccountDTO.getLastName();

		// Create organization name
		var tenantDTO = new TenantDTO();
		tenantDTO.setTenantName(companyName);
		tenantDTO.setSubscriptionInfo(new SubscriptionInfo());

		// Persist company Info
		var organizationInfo = this.tenantService.createOne(tenantDTO);

		// Update User DTO
		userDTO.setUserAccountStatus(UserAccountStatus.USER_ACTIVE.getValue());
		userDTO.setTenantId(organizationInfo.getId());

		// Persist user info
		var createdUser = this.userService.createOne(userDTO);

//		// Create Stripe Customer
		var stripeCustomerDTO = new StripeCustomerDTO();
		stripeCustomerDTO.setName(organizationInfo.getTenantName());
		stripeCustomerDTO.setEmail(userDTO.getEmail());
		stripeCustomerDTO.setTenantId(organizationInfo.getId());

		var stripeCustomer = this.stripeEventsService.createCustomer(stripeCustomerDTO);

		// Update Tenant Info
		organizationInfo.setStripeCustomerId(stripeCustomer.getCustomerId());
		var updatedTenant = this.tenantService.updateOne(organizationInfo.getId(), organizationInfo);

		log.debug("Updated Tenant {}", updatedTenant);

		// Send email to notify the user for his account creation
		this.accountCreationNotificationService.sendNotification(createdUser, newAccountDTO.getLanguageCode());

		// Return results
		return createdUser;
	}

	public UserDTO startSubscriptionProcess(CheckoutRequest request) throws QorvaException {
		return null;
	}

	public UserDTO finalizeSubscriptionProcess(String userId) throws QorvaException {
		return null;
	}
}
