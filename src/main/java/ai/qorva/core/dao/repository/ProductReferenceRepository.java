package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.ProductReference;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductReferenceRepository extends QorvaRepository<ProductReference> {

	List<ProductReference> findAllByActiveTrue();

	@Aggregation(pipeline = {
		"{ $match: { stripeProductId: ?0 }}"
	})
	ProductReference findByStripeProductId(String productId);

	@Aggregation(pipeline = {
		"{ $match: { 'prices.stripePriceId': ?0 }}"
	})
	ProductReference findByStripePriceId(String priceId);
}
