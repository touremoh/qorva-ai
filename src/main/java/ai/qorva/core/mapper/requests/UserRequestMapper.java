package ai.qorva.core.mapper.requests;

import ai.qorva.core.dto.UserDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface UserRequestMapper extends QorvaRequestMapper<UserDTO> {

	@Mapping(target = "authorities", ignore = true)
	UserDTO toDto(Map<String, String> params);
}
