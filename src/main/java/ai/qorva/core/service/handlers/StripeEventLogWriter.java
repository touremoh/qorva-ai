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
import org.springframework.stereotype.Component;

/**
 * Writes the stripe_event_logs row of an event that Qorva only records (invoices, customers, setup
 * intents, payment methods). The row belongs to a tenant, resolved from the Stripe customer.
 */
@Slf4j
@Component
public class StripeEventLogWriter {

	private final StripeEventLogRepository repository;
	private final StripeEventMapper evtMapper;
	private final TenantService tenantService;
	private final UserRepository userRepository;

	public StripeEventLogWriter(StripeEventLogRepository repository, StripeEventMapper evtMapper,
	                            TenantService tenantService, UserRepository userRepository) {
		this.repository = repository;
		this.evtMapper = evtMapper;
		this.tenantService = tenantService;
		this.userRepository = userRepository;
	}

	/** What an event contributes to its log row. {@code customerEmail} (optional) saves a Stripe lookup. */
	public record Entry(String stripeCustomerId, String customerEmail, String stripeSubscriptionId, String eventStatus) {}

	public void write(String eventType, Entry entry) {
		var tenantId = entry.stripeCustomerId() != null ? resolveTenantId(entry.stripeCustomerId(), entry.customerEmail()) : null;
		if (tenantId == null) {
			// The collection requires a tenant; writing anyway failed validation and turned into an
			// endless Stripe retry loop. An event for a customer Qorva cannot place is only logged here.
			log.warn("{} – no tenant for Stripe customer {}; not recorded", eventType, entry.stripeCustomerId());
			return;
		}
		var dto = new StripeEventLogDTO();
		dto.setEventType(eventType);
		dto.setStripeCustomerId(entry.stripeCustomerId());
		dto.setStripeSubscriptionId(entry.stripeSubscriptionId());
		dto.setEventStatus(entry.eventStatus());
		dto.setTenantId(tenantId);
		repository.save(evtMapper.map(dto));
		log.debug("{} event logged to stripe_event_logs for customer={}", eventType, entry.stripeCustomerId());
	}

	/**
	 * Two-step tenant resolution:
	 * 1. DB lookup by stripeCustomerId (works after checkout.session.completed stores it on the tenant)
	 * 2. Stripe Customer.retrieve() → email → userRepository.findByEmail() → tenantId
	 *    (covers pre-checkout events like customer.created, setup_intent.*, payment_method.attached)
	 */
	private String resolveTenantId(String stripeCustomerId, String knownEmail) {
		try {
			var tenant = tenantService.findOneByCriteria(TenantDTO.builder().stripeCustomerId(stripeCustomerId).build());
			if (tenant != null && tenant.getId() != null) {
				return tenant.getId();
			}
		} catch (QorvaException ignored) {
			// not yet stored — fall through to email-based lookup
		}

		String email = knownEmail;
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
		try {
			var user = userRepository.findByEmail(email);
			return user != null ? user.getTenantId() : null;
		} catch (Exception e) {
			log.warn("User lookup by email {} failed while resolving tenantId: {}", email, e.getMessage());
			return null;
		}
	}
}
