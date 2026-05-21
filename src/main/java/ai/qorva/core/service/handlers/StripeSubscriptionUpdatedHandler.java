package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.repository.StripeEventLogRepository;
import ai.qorva.core.dto.StripeEventLogDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.common.UserAuthority;
import ai.qorva.core.enums.SubscriptionPlanEnum;
import ai.qorva.core.enums.UserPermissionEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.StripeEventMapper;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.service.UserService;
import ai.qorva.core.utils.SubscriptionStatusHelper;
import com.stripe.exception.StripeException;
import com.stripe.model.Product;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class StripeSubscriptionUpdatedHandler implements StripeEventHandler {

	private final TenantService tenantService;
	private final UserService userService;
	private final StripeEventLogRepository repository;
	private final StripeEventMapper evtMapper;

	@Autowired
	public StripeSubscriptionUpdatedHandler(
		TenantService tenantService,
		UserService userService,
		StripeEventLogRepository repository,
		StripeEventMapper evtMapper
	) {
		this.tenantService = tenantService;
		this.userService = userService;
		this.repository = repository;
		this.evtMapper = evtMapper;
	}

	@Override
	public void handle(StripeObject obj) throws QorvaException {
		handle(obj, null);
	}

	@Override
	public void handle(StripeObject obj, String eventId) throws QorvaException {
		log.info("Handling customer.subscription.updated eventId={}", eventId);

		var sub = (Subscription) obj;
		var subscriptionId = sub.getId();
		var customerId = sub.getCustomer();
		var subscriptionStatus = SubscriptionStatusHelper.subscriptionFromCode(sub.getStatus());
		var subItem = sub.getItems().getData().getFirst();
		var productId = subItem.getPrice().getProduct();
		var priceId = subItem.getPrice().getId();

		try {
			var tenantDTO = tenantService.findOneByCriteria(TenantDTO.builder().stripeCustomerId(customerId).build());
			var product = Product.retrieve(productId);
			var currentPlan = tenantDTO.getSubscriptionInfo().getSubscriptionPlan();
			var currentPriceId = tenantDTO.getSubscriptionInfo().getPriceId();
			var newPlan = product.getName();

			if (!priceId.equals(currentPriceId) || !newPlan.equals(currentPlan)) {
				log.debug("Subscription product changed from {} to {} for customer {}", currentPlan, newPlan, customerId);

				var subscriptionDetails = Subscription.retrieve(subscriptionId);
				var detailItem = subscriptionDetails.getItems().getData().getFirst();
				var subscriptionInfo = tenantDTO.getSubscriptionInfo();

				subscriptionInfo.setSubscriptionId(subscriptionId);
				subscriptionInfo.setSubscriptionStatus(subscriptionStatus);
				subscriptionInfo.setSubscriptionStartDate(Instant.ofEpochSecond(subscriptionDetails.getStartDate()));
				subscriptionInfo.setSubscriptionPlan(newPlan);
				subscriptionInfo.setBillingCycle(detailItem.getPlan().getInterval());
				subscriptionInfo.setPrice(new Decimal128(detailItem.getPlan().getAmount()));
				subscriptionInfo.setPriceId(priceId);
				subscriptionInfo.setPlanCode(priceId);
				subscriptionInfo.setCurrentPeriodStart(detailItem.getCurrentPeriodStart() != null
					? Instant.ofEpochSecond(detailItem.getCurrentPeriodStart()) : null);
				subscriptionInfo.setCurrentPeriodEnd(detailItem.getCurrentPeriodEnd() != null
					? Instant.ofEpochSecond(detailItem.getCurrentPeriodEnd()) : null);
				subscriptionInfo.setCancelAtPeriodEnd(subscriptionDetails.getCancelAtPeriodEnd());
				tenantDTO.setSubscriptionInfo(subscriptionInfo);

				tenantService.updateOne(tenantDTO.getTenantId(), tenantDTO);
				updateUsersAuthorities(tenantDTO.getTenantId(), newPlan);
				persistEventInDb(tenantDTO, subscriptionId, customerId, subscriptionStatus, eventId);

			} else {
				var currentStatus = tenantDTO.getSubscriptionInfo().getSubscriptionStatus();
				if (!currentStatus.equals(subscriptionStatus)) {
					log.debug("Subscription status changed from {} to {} for customer {}", currentStatus, subscriptionStatus, customerId);

					var subscriptionInfo = tenantDTO.getSubscriptionInfo();
					subscriptionInfo.setSubscriptionStatus(subscriptionStatus);
					subscriptionInfo.setCurrentPeriodStart(subItem.getCurrentPeriodStart() != null
						? Instant.ofEpochSecond(subItem.getCurrentPeriodStart()) : null);
					subscriptionInfo.setCurrentPeriodEnd(subItem.getCurrentPeriodEnd() != null
						? Instant.ofEpochSecond(subItem.getCurrentPeriodEnd()) : null);
					subscriptionInfo.setCancelAtPeriodEnd(sub.getCancelAtPeriodEnd());
					tenantDTO.setSubscriptionInfo(subscriptionInfo);

					tenantService.updateOne(tenantDTO.getTenantId(), tenantDTO);
					persistEventInDb(tenantDTO, subscriptionId, customerId, subscriptionStatus, eventId);
				}
			}
		} catch (QorvaException e) {
			log.error("Failed to handle subscription update for customer {}", customerId, e);
			throw new QorvaException("Failed to handle subscription update for customer " + customerId, e);
		} catch (StripeException e) {
			log.error("Stripe API error for product {} customer {}", productId, customerId, e);
			throw new QorvaException("Stripe API error for productId=" + productId, e);
		}
	}

	protected void persistEventInDb(TenantDTO tenantDTO, String subscriptionId, String customerId,
		String subscriptionStatus, String eventId) {
		var eventLog = new StripeEventLogDTO();
		eventLog.setStripeEventId(eventId);
		eventLog.setEventType("customer.subscription.updated");
		eventLog.setEventStatus(subscriptionStatus);
		eventLog.setStripeCustomerId(customerId);
		eventLog.setStripeSubscriptionId(subscriptionId);
		eventLog.setTenantId(tenantDTO.getTenantId());
		repository.save(evtMapper.map(eventLog));
		log.debug("Saved subscription.updated event log for tenant={}", tenantDTO.getTenantId());
	}

	protected void updateUsersAuthorities(String tenantId, String newSubscriptionPlan) throws QorvaException {
		var permission = newSubscriptionPlan.equals(SubscriptionPlanEnum.STARTER.getName())
			? UserPermissionEnum.NOT_ALLOWED.getValue()
			: UserPermissionEnum.ALLOWED.getValue();

		int pageNumber = 0;
		Page<UserDTO> page;
		do {
			page = userService.findAll(Map.of("tenantId", tenantId, "pageSize", "25", "pageNumber", String.valueOf(pageNumber)));
			updateChatFeaturePermission(page.getContent(), permission);
			pageNumber++;
		} while (page.hasNext());
	}

	protected void updateChatFeaturePermission(List<UserDTO> users, String newPermission) throws QorvaException {
		for (var user : users) {
			var authorities = user.getAuthorities();
			var newAuthorities = new ArrayList<UserAuthority>();
			for (var authority : authorities) {
				if (authority.getAction().contains("CHAT")) {
					authority.setPermission(newPermission);
				}
				newAuthorities.add(authority);
			}
			user.setAuthorities(newAuthorities);
			userService.updateOne(user.getId(), user);
		}
	}
}
