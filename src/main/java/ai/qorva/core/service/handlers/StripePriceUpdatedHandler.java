package ai.qorva.core.service.handlers;

import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ProductReferenceService;
import com.stripe.model.Price;
import com.stripe.model.StripeObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StripePriceUpdatedHandler implements StripeEventHandler {

	private final ProductReferenceService productReferenceService;

	@Autowired
	public StripePriceUpdatedHandler(ProductReferenceService productReferenceService) {
		this.productReferenceService = productReferenceService;
	}

	@Override
	public void handle(StripeObject obj) throws QorvaException {
		Price price = (Price) obj;
		log.info("Handling price.updated stripePriceId={}", price.getId());
		productReferenceService.upsertPrice(price.getProduct(), StripePriceCreatedHandler.buildStripePrice(price));
	}
}
