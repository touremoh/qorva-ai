package ai.qorva.core.service;

import ai.qorva.core.dao.repository.ProductReferenceRepository;
import ai.qorva.core.dto.ProductReferenceDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.ProductReferenceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductReferenceService {
	private final ProductReferenceRepository repository;
	private final ProductReferenceMapper mapper;


	@Autowired
	public ProductReferenceService(ProductReferenceRepository repository, ProductReferenceMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	public ProductReferenceDTO findById(final String id, String referenceType) throws QorvaException {
		return switch (referenceType) {
			case "monthly" -> mapper.map(repository.findByMonthlyPriceId(id));
			case "yearly" -> mapper.map(repository.findByYearlyPriceId(id));
			case "product" -> mapper.map(repository.findByProductId(id));
			default -> throw new QorvaException("Invalid reference type provided: " + referenceType + ".");
		};
	}
}
