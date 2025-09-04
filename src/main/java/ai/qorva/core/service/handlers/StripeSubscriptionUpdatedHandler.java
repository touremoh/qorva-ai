package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.utils.SubscriptionStatusHelper;
import com.stripe.exception.StripeException;
import com.stripe.model.Product;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class StripeSubscriptionUpdatedHandler implements StripeEventHandler {

	private final TenantService tenantService;
	private final StripeEventLogRepository repository;
	private final StripeEventMapper evtMapper;

	@Autowired
	public StripeSubscriptionUpdatedHandler(TenantService tenantService, StripeEventLogRepository repository, StripeEventMapper evtMapper) {
		this.tenantService = tenantService;
		this.repository = repository;
		this.evtMapper = evtMapper;
	}

	@Override
	public void handle(StripeObject obj) throws QorvaException {
		log.info("Handling subscription update event");

		// Parse the event
		var sub = (Subscription) obj;

		// Get the relevant fields from the event: subscription ID, customer ID, status, product ID.
		var subscriptionId = sub.getId();
		var customerId = sub.getCustomer();
		var subscriptionStatus = SubscriptionStatusHelper.subscriptionFromCode(sub.getStatus());
		var subItem = sub.getItems().getData().getFirst();
		var productId = subItem.getPrice().getProduct();
		var priceId = subItem.getPrice().getId();

		try {
			// Get the tenant ID for the customer
			var tenantDTO = this.tenantService.findOneByData(TenantDTO.builder().stripeCustomerId(customerId).build());

			// Check if it's a product change
			var product = Product.retrieve(productId);
			var currentPlan = tenantDTO.getSubscriptionInfo().getSubscriptionPlan();
			var currentPriceId = tenantDTO.getSubscriptionInfo().getPriceId();
			var newPlan = product.getName();
			if (!priceId.equals(currentPriceId) || !newPlan.equals(currentPlan)) {
				log.debug("Subscription product changed from {} to {}", currentPlan, newPlan);

				var subscriptionInfo = tenantDTO.getSubscriptionInfo();
				var subscriptionDetails = Subscription.retrieve(subscriptionId);
				var subscriptionAmount = new Decimal128(subscriptionDetails.getItems().getData().getFirst().getPlan().getAmount());

				subscriptionInfo.setSubscriptionId(subscriptionId);
				subscriptionInfo.setSubscriptionStatus(subscriptionStatus);
				subscriptionInfo.setSubscriptionStartDate(Instant.ofEpochMilli(subscriptionDetails.getStartDate()));
				subscriptionInfo.setSubscriptionPlan(newPlan);
				subscriptionInfo.setBillingCycle(subscriptionDetails.getItems().getData().getFirst().getPlan().getInterval());
				subscriptionInfo.setPrice(subscriptionAmount);
				subscriptionInfo.setPriceId(priceId);
				tenantDTO.setSubscriptionInfo(subscriptionInfo);

				// Update tenant in database
				log.debug("Updating tenant in database: {}", tenantDTO);
				this.tenantService.updateOne(tenantDTO.getTenantId(), tenantDTO);
				this.persistEventInDb(tenantDTO, subscriptionId, customerId, subscriptionStatus);
			} else {
				// Check if it's a status change
				var currentSubscriptionStatus = tenantDTO.getSubscriptionInfo().getSubscriptionStatus();
				if (!currentSubscriptionStatus.equals(subscriptionStatus)) {
					log.debug("Subscription status changed from {} to {}", currentSubscriptionStatus, subscriptionStatus);
					this.persistEventInDb(tenantDTO, subscriptionId, customerId, subscriptionStatus);
				}
			}
		} catch (QorvaException e) {
			log.error("Failed to retrieve tenant details for customer {}", customerId);
			throw new QorvaException("Failed to retrieve tenant details for customer " + customerId, e);
		} catch (StripeException e) {
			log.error("Failed to retrieve product details for product id {}", productId);
			throw new QorvaException("Failed to retrieve product details for product id " + productId, e);
		}
	}

	protected void persistEventInDb(TenantDTO tenantDTO, String subscriptionId, String customerId, String subscriptionStatus) {
		// Persist stripe event logs
		var eventLog = new StripeEventLogDTO();
		eventLog.setEventType("customer.subscription.updated");
		eventLog.setEventStatus(subscriptionStatus);
		eventLog.setStripeCustomerId(customerId);
		eventLog.setStripeSubscriptionId(subscriptionId);
		eventLog.setTenantId(tenantDTO.getTenantId());

		// Persist the event log
		var savedEventLog = this.repository.save(this.evtMapper.map(eventLog));

		log.debug("Saved event log to database: {}", savedEventLog);
	}
}
