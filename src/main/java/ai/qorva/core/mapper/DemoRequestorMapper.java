package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.DemoRequestor;
import ai.qorva.core.dto.DemoRequestorDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DemoRequestorMapper extends AbstractQorvaMapper<DemoRequestor, DemoRequestorDTO> {
}
