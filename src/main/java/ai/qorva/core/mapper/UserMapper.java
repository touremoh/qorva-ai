package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dto.UserDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper extends AbstractQorvaMapper<User, UserDTO> {
}
