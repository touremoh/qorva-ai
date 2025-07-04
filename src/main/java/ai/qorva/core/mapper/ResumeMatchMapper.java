package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.ResumeMatch;
import ai.qorva.core.dto.ResumeMatchDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ResumeMatchMapper extends AbstractQorvaMapper<ResumeMatch, ResumeMatchDTO> {
}
