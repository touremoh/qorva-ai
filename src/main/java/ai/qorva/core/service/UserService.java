package ai.qorva.core.service;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.common.CompanyInfo;
import ai.qorva.core.enums.QorvaErrorsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Slf4j
@Service
public class UserService extends AbstractQorvaService<UserDTO, User> {

	private final PasswordEncoder passwordEncoder;
	private final UserRepository userRepository;

	@Autowired
	public UserService(UserRepository repository, UserMapper mapper, PasswordEncoder passwordEncoder) {
		super(repository, mapper);
		this.passwordEncoder = passwordEncoder;
		this.userRepository = repository;
	}

	@Override
	protected void preProcessCreateOne(UserDTO requestData) throws QorvaException {
		super.preProcessCreateOne(requestData);

		// Check if company id is present (mandatory for every request)
		if (Objects.nonNull(requestData.getCompanyInfo()) && !StringUtils.hasText(requestData.getCompanyInfo().tenantId())) {
			log.error("Missing company id for user {}", requestData);
			throw new QorvaException(
				"User creation requires a company id",
				HttpStatus.NOT_ACCEPTABLE.value(),
				HttpStatus.NOT_ACCEPTABLE
			);
		}

		// Check if user does not exist
		var userSearchCriteria = new UserDTO();
		userSearchCriteria.setEmail(requestData.getEmail());
		userSearchCriteria.setCompanyInfo(requestData.getCompanyInfo());

		if (this.existsByData(userSearchCriteria)) {
			log.error("Trying to create an existing user {}", requestData);
			throw new QorvaException(
				"Unable to create an existing user",
				HttpStatus.NOT_ACCEPTABLE.value(),
				HttpStatus.NOT_ACCEPTABLE
			);
		}

		// Encode password
		requestData.setEncryptedPassword(this.passwordEncoder.encode(requestData.getRawPassword()));
	}

	@Override
	protected void preProcessUpdateOne(String id, UserDTO userDTO) throws QorvaException {
		super.preProcessUpdateOne(id, userDTO);

		// Check if hotel user exists (find by ID)
		var userFound = this.findOneById(id);

		// If user found, update userDto empty field with the one from the db
		if (Objects.isNull(userFound)) {
			log.error("User {} not found", id);
			throw new QorvaException(
				"Unable to update user with invalid id",
				QorvaErrorsEnum.RESOURCE_NOT_FOUND.getHttpStatus().value(),
				QorvaErrorsEnum.RESOURCE_NOT_FOUND.getHttpStatus()
			);
		}

		// If user found then merge source with target
		this.mapper.merge(userDTO, userFound);
	}

	public UserDTO findOneByEmail(String email) throws QorvaException {
		var optionalUser = this.userRepository.findOneByEmail(email);

		if (optionalUser.isEmpty()) {
			log.error("User {} not found", email);
			throw new QorvaException("User not found with username: "+email);
		}
		return this.mapper.map(optionalUser.get());
	}
}
