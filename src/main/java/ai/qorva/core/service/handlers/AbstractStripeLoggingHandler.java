package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.service.TenantService;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractStripeLoggingHandler implements StripeEventHandler {

	protected final StripeEventLogRepository repository;
	protected final StripeEventMapper evtMapper;
	protected final TenantService tenantService;
	protected final UserRepository userRepository;

	protected AbstractStripeLoggingHandler(
		StripeEventLogRepository repository,
		StripeEventMapper evtMapper,
		TenantService tenantService,
		UserRepository userRepository
	) {
		this.repository = repository;
		this.evtMapper = evtMapper;
		this.tenantService = tenantService;
		this.userRepository = userRepository;
	}

	protected void saveEventLog(String eventType, String stripeCustomerId, String stripeSubscriptionId, String eventStatus) {
		saveEventLog(eventType, stripeCustomerId, null, stripeSubscriptionId, eventStatus);
	}

	/**
	 * Variant that accepts a known customer email to skip the Stripe API fallback call.
	 * Use this when the Customer object is already available (customer.created / customer.updated).
	 */
	protected void saveEventLog(String eventType, String stripeCustomerId, String customerEmail, String stripeSubscriptionId, String eventStatus) {
		var dto = new StripeEventLogDTO();
		dto.setEventType(eventType);
		dto.setStripeCustomerId(stripeCustomerId);
		dto.setStripeSubscriptionId(stripeSubscriptionId);
		dto.setEventStatus(eventStatus);

		if (stripeCustomerId != null) {
			String tenantId = resolveTenantId(stripeCustomerId, customerEmail);
			if (tenantId != null) {
				dto.setTenantId(tenantId);
			} else {
				log.warn("{} – could not resolve tenantId for Stripe customer {}, logging without tenantId", eventType, stripeCustomerId);
			}
		}

		repository.save(evtMapper.map(dto));
		log.debug("{} event logged to stripe_event_logs for customer={}", eventType, stripeCustomerId);
	}

	/**
	 * Two-step tenant resolution:
	 * 1. DB lookup by stripeCustomerId (works after checkout.session.completed stores it on the tenant)
	 * 2. Stripe Customer.retrieve() → email → userRepository.findByEmail() → tenantId
	 *    (covers pre-checkout events like customer.created, setup_intent.*, payment_method.attached)
	 */
	private String resolveTenantId(String stripeCustomerId, String knownEmail) {
		// Step 1: DB lookup
		try {
			var tenant = tenantService.findOneByCriteria(TenantDTO.builder().stripeCustomerId(stripeCustomerId).build());
			if (tenant != null && tenant.getId() != null) {
				return tenant.getId();
			}
		} catch (QorvaException ignored) {
			// not yet stored — fall through to email-based lookup
		}

		// Step 2a: use the email we already have (avoids an extra Stripe API call)
		String email = knownEmail;

		// Step 2b: fetch email from Stripe if not provided
		if (email == null) {
			try {
				email = Customer.retrieve(stripeCustomerId).getEmail();
			} catch (StripeException e) {
				log.warn("Could not retrieve Stripe customer {} to resolve tenantId: {}", stripeCustomerId, e.getMessage());
				return null;
			}
		}

		if (email == null) {
			return null;
		}

		// Step 2c: look up user by email → tenantId
		try {
			var user = userRepository.findByEmail(email);
			return user != null ? user.getTenantId() : null;
		} catch (Exception e) {
			log.warn("User lookup by email {} failed while resolving tenantId: {}", email, e.getMessage());
			return null;
		}
	}
}
