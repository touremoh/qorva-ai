package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.CandidateOutreach;
import ai.qorva.core.dto.CandidateOutreachDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CandidateOutreachMapper {
	CandidateOutreachDTO map(CandidateOutreach outreach);
}
