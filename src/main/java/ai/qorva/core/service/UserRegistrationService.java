package ai.qorva.core.service;

import ai.qorva.core.dto.AccountRegistrationDTO;
import ai.qorva.core.dto.CompanyDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AccountRegistrationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserRegistrationService {
	private final UserService userService;
	private final CompanyService companyService;
	private final AccountRegistrationMapper accountRegistrationMapper;
	private final AccountCreationNotificationService accountCreationNotificationService;

	@Autowired
	public UserRegistrationService(UserService userService, CompanyService companyService, AccountRegistrationMapper accountRegistrationMapper, AccountCreationNotificationService accountCreationNotificationService) {
		this.userService = userService;
		this.companyService = companyService;
		this.accountRegistrationMapper = accountRegistrationMapper;
		this.accountCreationNotificationService = accountCreationNotificationService;
	}

	public UserDTO createAccount(AccountRegistrationDTO newAccountDTO) throws QorvaException {
		// Create company info
		var createdCompany = this.companyService.createOne(CompanyDTO.builder().name(newAccountDTO.getCompanyName()).build());

		// Build user info
		var userDTO = this.accountRegistrationMapper.map(newAccountDTO, createdCompany);

		// Persist user info
		var createdUser = this.userService.createOne(userDTO);

		// Send email to notify the user for his account creation
		this.accountCreationNotificationService.sendNotification(createdUser, newAccountDTO.getLanguageCode());

		// Return results
		return createdUser;
	}
}
