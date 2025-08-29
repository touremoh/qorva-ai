package ai.qorva.core.service;

import ai.qorva.core.dto.AccountRegistrationDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.enums.SubscriptionStatus;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AccountRegistrationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class UserRegistrationService {
	private final UserService userService;
	private final TenantService tenantService;
	private final AccountRegistrationMapper accountRegistrationMapper;
	private final ObjectProvider<QorvaNotificationService> notifier;

	@Autowired
	public UserRegistrationService(
		UserService userService,
		TenantService tenantService,
		AccountRegistrationMapper accountRegistrationMapper,
		ObjectProvider<QorvaNotificationService> notifier
	) {
		this.userService = userService;
		this.tenantService = tenantService;
		this.accountRegistrationMapper = accountRegistrationMapper;
		this.notifier = notifier;
	}

	@Transactional
	public UserDTO createAccount(AccountRegistrationDTO newAccountDTO, String languageCode) throws QorvaException {
		log.info("Creating new account for user: {} - language {}", newAccountDTO, languageCode);

		var companyInfo = this.createCompanyInfo(newAccountDTO, languageCode);

		// Create user
		var newUser =  this.createNewUser(newAccountDTO, companyInfo);

		// Send email checker
		AtomicBoolean emailSent = new AtomicBoolean(false);

		// Send email to notify the user of his account creation
		this.notifier.ifAvailable(accountCreationNotificationSender -> {
			try {
				accountCreationNotificationSender.send(newUser, languageCode);
				emailSent.set(true);
			} catch (QorvaException e) {
				log.error("Failed to send email to user {}", newUser.getEmail(), e);
				try {
					var userId = newUser.getId();
					var tenantId = newUser.getTenantId();
					this.userService.deleteOneById(userId, tenantId);
					this.tenantService.deleteOneById(tenantId, tenantId);
				} catch (QorvaException ex) {
					log.error("Failed to delete user {} and tenant {}", newUser.getEmail(), newUser.getTenantId(), ex);
				}
			}
		});

		if (!emailSent.get()) {
			log.warn("Account creation failed {}", newUser.getEmail());
			throw new QorvaException("Account creation failed " + newAccountDTO.getEmail());
		}

		// Set subscription status
		newUser.setSubscriptionStatus(companyInfo.getSubscriptionInfo().getSubscriptionStatus());

		log.info("Account created successfully for user: {}", newUser);
		return newUser;
	}

	protected UserDTO createNewUser(AccountRegistrationDTO newAccountDTO, TenantDTO companyInfo) throws QorvaException {
		// Build user info
		var userDTO = this.accountRegistrationMapper.map(newAccountDTO);

		// Update User DTO
		userDTO.setUserAccountStatus(UserStatusEnum.ACTIVE.getValue());
		userDTO.setTenantId(companyInfo.getId());

		// Persist user info
		return this.userService.createOne(userDTO);
	}

	protected TenantDTO createCompanyInfo(AccountRegistrationDTO newAccountDTO, String languageCode) throws QorvaException {
		// Set language code
		newAccountDTO.setLanguageCode(languageCode);

		// Get the organization name (or take the name of user)
		final String companyName = StringUtils.hasText(newAccountDTO.getCompanyName())
			? newAccountDTO.getCompanyName()
			: newAccountDTO.getFirstName() + " " + newAccountDTO.getLastName();

		// Subscription info
		var subscriptionInfo = new SubscriptionInfo();
		subscriptionInfo.setSubscriptionStatus(SubscriptionStatus.SUBSCRIPTION_INCOMPLETE.getValue());

		// Create an organization
		var tenantDTO = new TenantDTO();
		tenantDTO.setTenantName(companyName);
		tenantDTO.setSubscriptionInfo(subscriptionInfo);

		// Persist company Info
		return this.tenantService.createOne(tenantDTO);
	}
}
