package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.entity.ProductReference;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ProductReferenceService;
import com.stripe.model.Product;
import com.stripe.model.StripeObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StripeProductCreatedHandler implements StripeEventHandler {

	private final ProductReferenceService productReferenceService;

	@Autowired
	public StripeProductCreatedHandler(ProductReferenceService productReferenceService) {
		this.productReferenceService = productReferenceService;
	}

	@Override
	public void handle(StripeObject obj) throws QorvaException {
		Product product = (Product) obj;
		log.info("Handling product.created stripeProductId={}", product.getId());

		ProductReference ref = new ProductReference();
		ref.setStripeProductId(product.getId());
		ref.setName(product.getName());
		ref.setDescription(product.getDescription());
		ref.setActive(Boolean.TRUE.equals(product.getActive()));
		ref.setMetadata(product.getMetadata());

		productReferenceService.upsertProduct(ref);
	}
}
