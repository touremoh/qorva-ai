package ai.qorva.core.service.handlers;

import ai.qorva.core.dto.common.StripePrice;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ProductReferenceService;
import com.stripe.model.Price;
import com.stripe.model.StripeObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StripePriceCreatedHandler implements StripeEventHandler {

	private final ProductReferenceService productReferenceService;

	@Autowired
	public StripePriceCreatedHandler(ProductReferenceService productReferenceService) {
		this.productReferenceService = productReferenceService;
	}

	@Override
	public void handle(StripeObject obj) throws QorvaException {
		Price price = (Price) obj;
		log.info("Handling price.created stripePriceId={}", price.getId());
		productReferenceService.upsertPrice(price.getProduct(), buildStripePrice(price));
	}

	static StripePrice buildStripePrice(Price price) {
		StripePrice stripePrice = new StripePrice();
		stripePrice.setStripePriceId(price.getId());
		stripePrice.setCurrency(price.getCurrency());
		stripePrice.setUnitAmount(price.getUnitAmount());
		stripePrice.setNickname(price.getNickname());
		stripePrice.setActive(Boolean.TRUE.equals(price.getActive()));

		Price.Recurring recurring = price.getRecurring();
		if (recurring != null) {
			stripePrice.setInterval(recurring.getInterval());
			stripePrice.setIntervalCount(recurring.getIntervalCount() != null ? recurring.getIntervalCount().intValue() : null);
		}
		return stripePrice;
	}
}
