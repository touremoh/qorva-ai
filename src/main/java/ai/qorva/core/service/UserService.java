package ai.qorva.core.service;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.QorvaRepository;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.enums.QorvaErrorsEnum;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.AbstractQorvaMapper;
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

	@Autowired
	public UserService(QorvaRepository<User> repository, AbstractQorvaMapper<User, UserDTO> mapper, PasswordEncoder passwordEncoder) {
		super(repository, mapper);
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	protected void preProcessCreateOne(UserDTO userDTO) throws QorvaException {
		super.preProcessCreateOne(userDTO);

		// Check if company id is present (mandatory for every request)
		if (!StringUtils.hasText(userDTO.getCompanyId())) {
			log.error("Missing company id for user {}", userDTO);
			throw new QorvaException(
				"User creation requires a company id",
				HttpStatus.NOT_ACCEPTABLE.value(),
				HttpStatus.NOT_ACCEPTABLE
			);
		}

		// Check if user does not exist
		if (this.existsByData(userDTO)) {
			log.error("Trying to create an existing user {}", userDTO);
			throw new QorvaException(
				"Unable to create an existing user",
				HttpStatus.NOT_ACCEPTABLE.value(),
				HttpStatus.NOT_ACCEPTABLE
			);
		}

		// Encode password
		userDTO.setEncryptedPassword(this.passwordEncoder.encode(userDTO.getRawPassword()));

		// Set status of the user
		userDTO.setAccountStatus(UserStatusEnum.TRIAL_PERIOD.getValue());
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
}