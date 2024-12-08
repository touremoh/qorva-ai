package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.Company;
import ai.qorva.core.dto.CompanyDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CompanyMapper extends AbstractQorvaMapper<Company, CompanyDTO> {
}
