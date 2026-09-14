package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.Note;
import ai.qorva.core.dto.NoteDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NoteMapper {
	NoteDTO map(Note note);
}
