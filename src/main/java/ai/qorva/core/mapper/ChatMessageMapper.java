package ai.qorva.core.mapper;


import ai.qorva.core.dao.entity.ChatMessage;
import ai.qorva.core.dto.ChatMessageDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ChatMessageMapper extends AbstractQorvaMapper<ChatMessage, ChatMessageDTO> {

    @Mapping(target = "role", expression = "java(entity.getRole().name())")
    ChatMessageDTO map(ChatMessage entity);

    @InheritInverseConfiguration(name = "map")
    @Mapping(target = "role", expression = "java(ai.qorva.core.enums.ChatUserRole.valueOf(dto.getRole()))")
    ChatMessage map(ChatMessageDTO dto);
}
