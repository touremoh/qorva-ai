package ai.qorva.core.mapper;

import ai.qorva.core.dao.entity.InterviewQuestions;
import ai.qorva.core.dto.InterviewQuestionsDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InterviewQuestionsMapper extends AbstractQorvaMapper<InterviewQuestions, InterviewQuestionsDTO> {
}
