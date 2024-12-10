package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dto.UserDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper extends AbstractQorvaMapper<User, UserDTO> {

	@Mapping(target = "rawPassword", ignore = true)
	UserDTO map(User o);
}
