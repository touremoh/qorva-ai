package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dto.TenantDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TenantMapper extends AbstractQorvaMapper<Tenant, TenantDTO> {
}
