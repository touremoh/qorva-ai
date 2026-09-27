package ai.qorva.core.service.handlers;

import ai.qorva.core.dao.entity.ProductReference;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ProductReferenceService;
import com.stripe.model.Product;
import com.stripe.model.StripeObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/** product.created / product.updated: keeps the local product catalogue in step with Stripe. */
@Slf4j
@Service
public class StripeProductHandler implements StripeEventHandler {

	private final ProductReferenceService productReferenceService;

	public StripeProductHandler(ProductReferenceService productReferenceService) {
		this.productReferenceService = productReferenceService;
	}

	@Override
	public Set<String> eventTypes() {
		return Set.of("product.created", "product.updated");
	}

	@Override
	public Class<? extends StripeObject> objectType() {
		return Product.class;
	}

	@Override
	public void handle(StripeObject obj) throws QorvaException {
		Product product = (Product) obj;
		log.info("Handling product event stripeProductId={}", product.getId());

		ProductReference ref = new ProductReference();
		ref.setStripeProductId(product.getId());
		ref.setName(product.getName());
		ref.setDescription(product.getDescription());
		ref.setActive(Boolean.TRUE.equals(product.getActive()));
		ref.setMetadata(product.getMetadata());

		productReferenceService.upsertProduct(ref);
	}
}
