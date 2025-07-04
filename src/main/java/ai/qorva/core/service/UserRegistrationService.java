package ai.qorva.core.service;

import ai.qorva.core.dto.AccountRegistrationDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.enums.BillingCycle;
import ai.qorva.core.enums.SubscriptionPlanEnum;
import ai.qorva.core.enums.SubscriptionStatus;
import ai.qorva.core.enums.UserAccountStatus;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AccountRegistrationMapper;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Service
public class UserRegistrationService {
	private final UserService userService;
	private final TenantService tenantService;
	private final AccountRegistrationMapper accountRegistrationMapper;
	private final AccountCreationNotificationService accountCreationNotificationService;

	@Autowired
	public UserRegistrationService(UserService userService, TenantService tenantService, AccountRegistrationMapper accountRegistrationMapper, AccountCreationNotificationService accountCreationNotificationService) {
		this.userService = userService;
		this.tenantService = tenantService;
		this.accountRegistrationMapper = accountRegistrationMapper;
		this.accountCreationNotificationService = accountCreationNotificationService;
	}

	public UserDTO createAccount(AccountRegistrationDTO newAccountDTO, String languageCode) throws QorvaException {
		// Set language code
		newAccountDTO.setLanguageCode(languageCode);

		// Build user info
		var userDTO = this.accountRegistrationMapper.map(newAccountDTO);

		// Get the organization name (or take the name of user)
		final String companyName = StringUtils.hasText(newAccountDTO.getCompanyName())
			? newAccountDTO.getCompanyName()
			: newAccountDTO.getFirstName() + " " + newAccountDTO.getLastName();

		// Init subscription info
		var subscriptionInfo = new SubscriptionInfo();

		subscriptionInfo.setSubscriptionPlan(SubscriptionPlanEnum.FREE_TRIAL.getName());
		subscriptionInfo.setBillingCycle(BillingCycle.MONTHLY.getValue());
		subscriptionInfo.setPrice(new Decimal128(0L));
		subscriptionInfo.setSubscriptionStatus(SubscriptionStatus.SUBSCRIPTION_ACTIVE.getValue());
		subscriptionInfo.setSubscriptionId(UUID.randomUUID().toString().toUpperCase());

		// Create organization name
		var tenantDTO = new TenantDTO();
		tenantDTO.setTenantName(companyName);
		tenantDTO.setSubscriptionInfo(subscriptionInfo);

		// Persist company Info
		var organizationInfo = this.tenantService.createOne(tenantDTO);

		// Update User DTO
		userDTO.setUserAccountStatus(UserAccountStatus.USER_ACTIVE.getValue());
		userDTO.setTenantId(organizationInfo.getId());

		// Persist user info
		var createdUser = this.userService.createOne(userDTO);

		// Send email to notify the user for his account creation
		this.accountCreationNotificationService.sendNotification(createdUser, newAccountDTO.getLanguageCode());

		// Return results
		return createdUser;
	}

	public UserDTO startSubscriptionProcess(String userId) throws QorvaException {
		return null;
	}

	public UserDTO finalizeSubscriptionProcess(String userId) throws QorvaException {
		return null;
	}
}
