package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.Client;
import ai.qorva.core.dto.ClientDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClientMapper extends AbstractQorvaMapper<Client, ClientDTO> {
}
