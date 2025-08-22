package ai.qorva.core.mapper.requests;

import ai.qorva.core.dto.UserDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserRequestMapper extends QorvaRequestMapper<UserDTO> {
}
