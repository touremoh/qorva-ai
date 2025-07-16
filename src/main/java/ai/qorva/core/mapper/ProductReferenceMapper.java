package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.ProductReference;
import ai.qorva.core.dto.ProductReferenceDTO;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductReferenceMapper extends AbstractQorvaMapper<ProductReference, ProductReferenceDTO> {

	@Mapping(target = "tenantId",  ignore = true)
	ProductReferenceDTO map(ProductReference o);

	@InheritInverseConfiguration
	ProductReference map(ProductReferenceDTO o);
}
