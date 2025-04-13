package ai.qorva.core.service;

import ai.qorva.core.dto.AccountRegistrationDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.common.CompanyInfo;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.enums.*;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AccountRegistrationMapper;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class UserRegistrationService {
	private final UserService userService;
	private final AccountRegistrationMapper accountRegistrationMapper;
	private final AccountCreationNotificationService accountCreationNotificationService;

	@Autowired
	public UserRegistrationService(UserService userService, AccountRegistrationMapper accountRegistrationMapper, AccountCreationNotificationService accountCreationNotificationService) {
		this.userService = userService;
		this.accountRegistrationMapper = accountRegistrationMapper;
		this.accountCreationNotificationService = accountCreationNotificationService;
	}

	public UserDTO createAccount(AccountRegistrationDTO newAccountDTO) throws QorvaException {
		// Build user info
		var userDTO = this.accountRegistrationMapper.map(newAccountDTO);

		// Generate an ID for the organization of the user
		var tenantId = UUID.randomUUID().toString().toUpperCase();

		// Get the organization name (or take the name of user)
		final String companyName = StringUtils.hasText(newAccountDTO.getCompanyName())
			? newAccountDTO.getCompanyName()
			: newAccountDTO.getFirstName() + " " + newAccountDTO.getLastName();

		// Init company info
		var companyInfo = new CompanyInfo(companyName, tenantId, Instant.now(), Instant.now());

		// Init subscription info
		var subscriptionInfo = new SubscriptionInfo();

		subscriptionInfo.setSubscriptionPlan(SubscriptionPlanEnum.FREE_TRIAL.getName());
		subscriptionInfo.setBillingCycle(BillingCycle.MONTHLY.getValue());
		subscriptionInfo.setPrice(new Decimal128(0L));
		subscriptionInfo.setSubscriptionStatus(SubscriptionStatus.SUBSCRIPTION_ACTIVE.getValue());
		subscriptionInfo.setSubscriptionId(UUID.randomUUID().toString().toUpperCase());
		subscriptionInfo.setDashboardAccessType(DashboardAccessType.FULL.getValue());
		subscriptionInfo.setHasAiQuestion(true);

		// Update User DTO
		userDTO.setCompanyInfo(companyInfo);
		userDTO.setSubscriptionInfo(subscriptionInfo);
		userDTO.setCreatedBy("system");
		userDTO.setLastUpdatedBy("system");
		userDTO.setUserAccountStatus(UserAccountStatus.USER_ACTIVE.getValue());

		// Persist user info
		var createdUser = this.userService.createOne(userDTO);

		// Send email to notify the user for his account creation
		this.accountCreationNotificationService.sendNotification(createdUser, newAccountDTO.getLanguageCode());

		// Return results
		return createdUser;
	}
}
