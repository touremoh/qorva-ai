package ai.qorva.core.runner;

import ai.qorva.core.config.StripeProperties;
import ai.qorva.core.dao.entity.ProductReference;
import ai.qorva.core.dto.common.StripePrice;
import ai.qorva.core.service.ProductReferenceService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import com.stripe.model.PriceCollection;
import com.stripe.model.Product;
import com.stripe.model.ProductCollection;
import com.stripe.param.PriceListParams;
import com.stripe.param.ProductListParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class StripeProductSyncRunner implements CommandLineRunner {

	private final ProductReferenceService productReferenceService;
	private final StripeProperties stripeProperties;

	@Autowired
	public StripeProductSyncRunner(ProductReferenceService productReferenceService, StripeProperties stripeProperties) {
		this.productReferenceService = productReferenceService;
		this.stripeProperties = stripeProperties;
	}

	@Override
	public void run(String... args) {
		Stripe.apiKey = stripeProperties.getSecretKey();
		log.info("Starting Stripe product catalog sync...");

		try {
			syncProducts();
			log.info("Stripe product catalog sync completed.");
		} catch (StripeException e) {
			log.error("Stripe product catalog sync failed", e);
		}
	}

	private void syncProducts() throws StripeException {
		ProductListParams productParams = ProductListParams.builder()
			.setLimit(100L)
			.build();

		ProductCollection products = Product.list(productParams);

		for (Product product : products.autoPagingIterable()) {
			upsertProduct(product);
			syncPricesForProduct(product.getId());
		}
	}

	private void upsertProduct(Product product) {
		ProductReference ref = new ProductReference();
		ref.setStripeProductId(product.getId());
		ref.setName(product.getName());
		ref.setDescription(product.getDescription());
		ref.setActive(Boolean.TRUE.equals(product.getActive()));
		ref.setMetadata(product.getMetadata());
		productReferenceService.upsertProduct(ref);
	}

	private void syncPricesForProduct(String stripeProductId) throws StripeException {
		PriceListParams priceParams = PriceListParams.builder()
			.setProduct(stripeProductId)
			.setLimit(100L)
			.build();

		PriceCollection prices = Price.list(priceParams);

		for (Price price : prices.autoPagingIterable()) {
			productReferenceService.upsertPrice(stripeProductId, toStripePrice(price));
		}
	}

	private StripePrice toStripePrice(Price price) {
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
