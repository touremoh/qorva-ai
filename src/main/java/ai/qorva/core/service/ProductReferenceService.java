package ai.qorva.core.service;

import ai.qorva.core.dao.entity.ProductReference;
import ai.qorva.core.dao.repository.ProductReferenceRepository;
import ai.qorva.core.dto.ProductReferenceDTO;
import ai.qorva.core.dto.common.StripePrice;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.ProductReferenceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class ProductReferenceService {

	private final ProductReferenceRepository repository;
	private final ProductReferenceMapper mapper;

	@Autowired
	public ProductReferenceService(ProductReferenceRepository repository, ProductReferenceMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	public List<ProductReferenceDTO> findAllActive() {
		return repository.findAllByActiveTrue().stream().map(mapper::map).toList();
	}

	public ProductReferenceDTO findByStripeProductId(String productId) throws QorvaException {
		return mapper.map(repository.findByStripeProductId(productId));
	}

	public ProductReferenceDTO findByStripePriceId(String priceId) throws QorvaException {
		return mapper.map(repository.findByStripePriceId(priceId));
	}

	public void upsertProduct(ProductReference product) {
		ProductReference existing = repository.findByStripeProductId(product.getStripeProductId());
		if (existing != null) {
			existing.setName(product.getName());
			existing.setDescription(product.getDescription());
			existing.setActive(product.isActive());
			existing.setMetadata(product.getMetadata());
			repository.save(existing);
			log.info("Updated product reference stripeProductId={}", product.getStripeProductId());
		} else {
			repository.save(product);
			log.info("Created product reference stripeProductId={}", product.getStripeProductId());
		}
	}

	public void upsertPrice(String stripeProductId, StripePrice price) {
		ProductReference product = repository.findByStripeProductId(stripeProductId);
		if (product == null) {
			log.warn("Product not found for stripeProductId={}, skipping price upsert", stripeProductId);
			return;
		}
		if (product.getPrices() == null) {
			product.setPrices(new ArrayList<>());
		}
		product.getPrices().removeIf(p -> Objects.equals(p.getStripePriceId(), price.getStripePriceId()));
		product.getPrices().add(price);
		repository.save(product);
		log.info("Upserted price stripePriceId={} for stripeProductId={}", price.getStripePriceId(), stripeProductId);
	}
}
