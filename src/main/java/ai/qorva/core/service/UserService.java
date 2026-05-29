package ai.qorva.core.service;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.enums.QorvaErrorsEnum;
import ai.qorva.core.enums.SubscriptionPlanEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.UserMapper;
import ai.qorva.core.dao.querybuilder.UserQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
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
	private final TenantService tenantService;

	@Autowired
	public UserService(UserRepository repository, UserMapper mapper, PasswordEncoder passwordEncoder,
	                   UserQueryBuilder queryBuilder, TenantService tenantService) {
		super(repository, mapper, queryBuilder);
		this.passwordEncoder = passwordEncoder;
		this.tenantService = tenantService;
	}

	@Override
	protected void preProcessCreateOne(UserDTO dto) throws QorvaException {
		super.preProcessCreateOne(dto);

		if (!StringUtils.hasText(dto.getTenantId())) {
			log.error("Missing company id for user {}", dto);
			throw new QorvaException(
				"User creation requires a company id",
				HttpStatus.NOT_ACCEPTABLE.value(),
				HttpStatus.NOT_ACCEPTABLE
			);
		}

		// Prevent duplicate accounts
		var userFound = ((UserRepository) repository).findByEmail(dto.getEmail());
		if (Optional.ofNullable(userFound).isPresent()) {
			log.error("Trying to create an existing user {}", dto);
			throw new QorvaException(
				"User already exists",
				HttpStatus.NOT_ACCEPTABLE.value(),
				HttpStatus.NOT_ACCEPTABLE
			);
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
				// Unknown plan – allow creation but log a warning
				log.warn("Unknown subscription plan '{}' for tenant {} – skipping seat limit check", planName, tenantId);
				return;
			}

			int maxSeats = plan.get().getMaxSeats();
			if (maxSeats == Integer.MAX_VALUE) {
				return; // Unlimited seats
			}

			long currentUsers = countAll(tenantId);
			if (currentUsers >= maxSeats) {
				log.warn("Tenant {} has reached the seat limit ({}) for plan {}", tenantId, maxSeats, planName);
				throw new QorvaException(
					"Seat limit reached for your subscription plan. Please upgrade to add more users.",
					HttpStatus.PAYMENT_REQUIRED.value(),
					HttpStatus.PAYMENT_REQUIRED
				);
			}
		} catch (QorvaException e) {
			throw e;
		} catch (Exception e) {
			log.warn("Could not verify seat limit for tenant {} – allowing creation", tenantId, e);
		}
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
			throw new QorvaException("User not found", HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND);
		}

		if (!passwordEncoder.matches(currentPassword, user.getEncryptedPassword())) {
			throw new QorvaException("Current password is incorrect", HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
		}

		user.setEncryptedPassword(passwordEncoder.encode(newPassword));
		repository.save(user);
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
