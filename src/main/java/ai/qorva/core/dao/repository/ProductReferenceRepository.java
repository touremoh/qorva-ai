package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.ProductReference;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductReferenceRepository extends QorvaRepository<ProductReference> {

	@Aggregation(pipeline = {
		"{ $match: { stripeProductId: ?0 }}"
	})
	ProductReference findByProductId(String productId);

	@Aggregation(pipeline = {
		"{ $match: { 'price.monthlyPriceId': ?0 }}"
	})
	ProductReference findByMonthlyPriceId(String priceId);

	@Aggregation(pipeline = {
		"{ $match: { 'price.annualPriceId': ?0 }}"
	})
	ProductReference findByYearlyPriceId(String priceId);
}
