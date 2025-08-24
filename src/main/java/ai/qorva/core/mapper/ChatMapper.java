package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.dto.ChatDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ChatMapper extends AbstractQorvaMapper<Chat, ChatDTO> {

    @Mapping(target = "status", expression = "java(chat.getStatus().name())")
    ChatDTO map(Chat chat);

    @InheritInverseConfiguration(name = "map")
    @Mapping(target = "status", expression = "java(ai.qorva.core.enums.ChatStatus.valueOf(dto.getStatus()))")
    Chat map(ChatDTO dto);
}
