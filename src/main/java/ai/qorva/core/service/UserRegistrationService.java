package ai.qorva.core.service;

import ai.qorva.core.dto.AccountRegistrationDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.enums.SubscriptionStatus;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.helpers.UserAuthoritiesHelper;
import ai.qorva.core.mapper.AccountRegistrationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

	/**
	 * Creates a new tenant + user account, then attempts to send a welcome email.
	 * Email failure is non-fatal: the account is always persisted. A failed email is logged
	 * for manual retry or monitoring, but never causes a rollback.
	 */
	public UserDTO createAccount(AccountRegistrationDTO newAccountDTO, String languageCode) throws QorvaException {
		log.info("Creating new account for user: {} – language: {}", newAccountDTO.getEmail(), languageCode);

		var companyInfo = createCompanyInfo(newAccountDTO, languageCode);
		var newUser = createNewUser(newAccountDTO, companyInfo);

		// Best-effort email: log failure but never roll back the account
		notifier.ifAvailable(sender -> {
			try {
				sender.send(newUser, languageCode);
				log.info("Welcome email sent to {}", newUser.getEmail());
			} catch (QorvaException e) {
				log.error("Failed to send welcome email to {} – account created successfully, email will need manual retry",
					newUser.getEmail(), e);
			}
		});

		newUser.setSubscriptionStatus(companyInfo.getSubscriptionInfo().getSubscriptionStatus());
		log.info("Account created successfully for user: {}", newUser.getEmail());
		return newUser;
	}

	protected UserDTO createNewUser(AccountRegistrationDTO newAccountDTO, TenantDTO companyInfo) throws QorvaException {
		var userDTO = accountRegistrationMapper.map(newAccountDTO);
		userDTO.setUserAccountStatus(UserStatusEnum.ACTIVE.getValue());
		userDTO.setTenantId(companyInfo.getId());
		userDTO.setAuthorities(UserAuthoritiesHelper.createAuthorities());
		return userService.createOne(userDTO);
	}

	protected TenantDTO createCompanyInfo(AccountRegistrationDTO newAccountDTO, String languageCode) throws QorvaException {
		newAccountDTO.setLanguageCode(languageCode);

		final String companyName = StringUtils.hasText(newAccountDTO.getCompanyName())
			? newAccountDTO.getCompanyName()
			: newAccountDTO.getFirstName() + " " + newAccountDTO.getLastName();

		var subscriptionInfo = new SubscriptionInfo();
		subscriptionInfo.setSubscriptionStatus(SubscriptionStatus.SUBSCRIPTION_INCOMPLETE.getValue());

		var tenantDTO = new TenantDTO();
		tenantDTO.setTenantName(companyName);
		tenantDTO.setSubscriptionInfo(subscriptionInfo);

		return tenantService.createOne(tenantDTO);
	}
}
