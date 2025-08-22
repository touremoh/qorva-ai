package ai.qorva.core.service;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.enums.QorvaErrorsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.UserMapper;
import ai.qorva.core.qbe.UserQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class UserService extends AbstractQorvaService<UserDTO, User> {

	private final PasswordEncoder passwordEncoder;

	@Autowired
	public UserService(UserRepository repository, UserMapper mapper, PasswordEncoder passwordEncoder, UserQueryBuilder queryBuilder) {
		super(repository, mapper, queryBuilder);
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	protected void preProcessCreateOne(UserDTO dto) throws QorvaException {
		super.preProcessCreateOne(dto);

		// Check if company id is present (mandatory for every request)
		if (!StringUtils.hasText(dto.getTenantId())) {
			log.error("Missing company id for user {}", dto);
			throw new QorvaException(
				"User creation requires a company id",
				HttpStatus.NOT_ACCEPTABLE.value(),
				HttpStatus.NOT_ACCEPTABLE
			);
		}

		// Check if the user does not exist
		var userFound = ((UserRepository)repository).findByEmail(dto.getEmail());

		if (Optional.ofNullable(userFound).isPresent()) {
			log.error("Trying to create an existing user {}", dto);
			throw new QorvaException(
				"User already exists",
				HttpStatus.NOT_ACCEPTABLE.value(),
				HttpStatus.NOT_ACCEPTABLE
			);
		}

		// Encode password
		dto.setEncryptedPassword(this.passwordEncoder.encode(dto.getRawPassword()));
	}

	@Override
	protected void preProcessUpdateOne(String id, UserDTO userDTO) throws QorvaException {
		super.preProcessUpdateOne(id, userDTO);

		// Check if the user exists (find by ID)
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

		// If user found then merge the source with the target
		this.mapper.merge(userDTO, userFound);
	}

	public long updateUserAccountStatusByTenantId(String tenantId, String newStatus) {
		return ((UserRepository) this.repository).updateUserAccountStatusByTenantId(tenantId, newStatus);
	}

	@Override
	protected void preProcessFindOneByData(UserDTO requestData) {
		if (!StringUtils.hasText(requestData.getEmail())
			&& !StringUtils.hasText(requestData.getId())
			&& !StringUtils.hasText(requestData.getTenantId())) {
				throw new IllegalArgumentException("Either email or id or tenantId must be present");
			}

	}

	public UserDTO findOneByEmail(String email) {
		return this.mapper.map(((UserRepository)this.repository).findByEmail(email));
	}
}
