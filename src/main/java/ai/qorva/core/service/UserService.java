package ai.qorva.core.service;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.request.AddUserRequest;
import ai.qorva.core.enums.EmailNotificationType;
import ai.qorva.core.enums.QorvaErrorsEnum;
import ai.qorva.core.enums.SubscriptionPlanEnum;
import ai.qorva.core.enums.UserStatusEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.UserMapper;
import ai.qorva.core.dao.querybuilder.UserQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import ai.qorva.core.dto.common.UserAuthority;
import org.bson.types.ObjectId;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class UserService extends AbstractQorvaService<UserDTO, User> {

	private static final String TEMP_PASSWORD_CHARS =
		"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$!";
	private static final int TEMP_PASSWORD_LENGTH = 12;

	private final PasswordEncoder passwordEncoder;
	private final TenantService tenantService;
	private final PendingEmailNotificationService pendingEmailNotificationService;

	@Autowired
	public UserService(UserRepository repository, UserMapper mapper, PasswordEncoder passwordEncoder,
	                   UserQueryBuilder queryBuilder, TenantService tenantService,
	                   PendingEmailNotificationService pendingEmailNotificationService) {
		super(repository, mapper, queryBuilder);
		this.passwordEncoder = passwordEncoder;
		this.tenantService = tenantService;
		this.pendingEmailNotificationService = pendingEmailNotificationService;
	}

	@Override
	protected void preProcessCreateOne(UserDTO dto) throws QorvaException {
		super.preProcessCreateOne(dto);

		if (!StringUtils.hasText(dto.getTenantId())) {
			log.error("Missing company id for user {}", dto);
			throw new QorvaException(QorvaErrorCodes.USER_COMPANY_ID_REQUIRED, HttpStatus.NOT_ACCEPTABLE.value(), HttpStatus.NOT_ACCEPTABLE);
		}

		// Prevent duplicate accounts
		var userFound = ((UserRepository) repository).findByEmail(dto.getEmail());
		if (Optional.ofNullable(userFound).isPresent()) {
			log.error("Trying to create an existing user {}", dto);
			throw new QorvaException(QorvaErrorCodes.USER_ALREADY_EXISTS, HttpStatus.NOT_ACCEPTABLE.value(), HttpStatus.NOT_ACCEPTABLE);
		}

		// Seat-limit enforcement based on subscription plan
		enforceSeatLimit(dto.getTenantId());

		// Encode password
		dto.setEncryptedPassword(this.passwordEncoder.encode(dto.getRawPassword()));
	}

	private void enforceSeatLimit(String tenantId) throws QorvaException {
		try {
			var tenant = tenantService.findOneById(tenantId);
			var planName = tenant.getSubscriptionInfo() != null
				? tenant.getSubscriptionInfo().getSubscriptionPlan()
				: null;

			var plan = SubscriptionPlanEnum.fromName(planName);
			if (plan.isEmpty()) {
				log.warn("Unknown subscription plan '{}' for tenant {} – skipping seat limit check", planName, tenantId);
				return;
			}

			int maxSeats = plan.get().getMaxSeats();
			if (maxSeats == Integer.MAX_VALUE) {
				return;
			}

			long currentUsers = countAll(tenantId);
			if (currentUsers >= maxSeats) {
				log.warn("Tenant {} has reached the seat limit ({}) for plan {}", tenantId, maxSeats, planName);
				throw new QorvaException(QorvaErrorCodes.USER_SEAT_LIMIT_REACHED, HttpStatus.PAYMENT_REQUIRED.value(), HttpStatus.PAYMENT_REQUIRED);
			}
		} catch (QorvaException e) {
			throw e;
		} catch (Exception e) {
			log.warn("Could not verify seat limit for tenant {} – allowing creation", tenantId, e);
		}
	}

	public UserDTO addUser(String tenantId, AddUserRequest request) throws QorvaException {
		String lang = StringUtils.hasText(request.getCommunicationLanguage())
			? request.getCommunicationLanguage() : "en";

		String companyName = resolveCompanyName(tenantId);
		String tempPassword = generateTemporaryPassword();

		var userDTO = new UserDTO();
		userDTO.setTenantId(tenantId);
		userDTO.setFirstName(request.getFirstName());
		userDTO.setLastName(request.getLastName());
		userDTO.setEmail(request.getEmail());
		userDTO.setRawPassword(tempPassword);
		userDTO.setAuthorities(request.getAuthorities());
		userDTO.setUserAccountStatus(UserStatusEnum.ACTIVE.getValue());
		userDTO.setCommunicationLanguage(lang);

		var created = createOne(userDTO);

		pendingEmailNotificationService.createPending(
			tenantId, created.getId(), EmailNotificationType.USER_ADDED, lang,
			Map.of("temporaryPassword", tempPassword, "companyName", companyName)
		);

		log.info("User invited: tenantId={} email={}", tenantId, request.getEmail());
		return created;
	}

	private String resolveCompanyName(String tenantId) {
		try {
			var tenant = tenantService.findOneById(tenantId);
			return tenant.getTenantName() != null ? tenant.getTenantName() : "";
		} catch (Exception e) {
			log.warn("Could not retrieve tenant name for tenantId={}", tenantId);
			return "";
		}
	}

	private String generateTemporaryPassword() {
		var random = new SecureRandom();
		var sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
		for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
			sb.append(TEMP_PASSWORD_CHARS.charAt(random.nextInt(TEMP_PASSWORD_CHARS.length())));
		}
		return sb.toString();
	}

	@Override
	protected void preProcessUpdateOne(String id, UserDTO userDTO) throws QorvaException {
		super.preProcessUpdateOne(id, userDTO);
		this.mapper.merge(userDTO, getExistingForUpdate());
	}

	public void updatePassword(String tenantId, String userId, String currentPassword, String newPassword) throws QorvaException {
		var user = repository.findById(new ObjectId(userId))
			.orElseThrow(() -> new QorvaException("User not found", HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND));

		if (!tenantId.equals(user.getTenantId())) {
			throw new QorvaException(QorvaErrorCodes.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND);
		}

		if (!passwordEncoder.matches(currentPassword, user.getEncryptedPassword())) {
			throw new QorvaException(QorvaErrorCodes.USER_PASSWORD_INCORRECT, HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
		}

		user.setEncryptedPassword(passwordEncoder.encode(newPassword));
		repository.save(user);
	}

	public void updateAuthorities(String tenantId, String userId, List<UserAuthority> authorities) throws QorvaException {
		var user = repository.findById(new ObjectId(userId))
			.orElseThrow(() -> new QorvaException(QorvaErrorCodes.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND));

		if (!tenantId.equals(user.getTenantId())) {
			throw new QorvaException(QorvaErrorCodes.USER_NOT_FOUND, HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND);
		}

		user.setAuthorities(authorities);
		repository.save(user);
		log.info("User authorities updated: tenantId={} userId={}", tenantId, userId);
	}

	public UserDTO findByEmail(String email) {
		var entity = ((UserRepository) this.repository).findByEmail(email);
		return entity != null ? mapper.map(entity) : null;
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
}
