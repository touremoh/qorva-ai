package ai.qorva.core.service;

import ai.qorva.core.dao.entity.InterviewQuestions;
import ai.qorva.core.dao.repository.InterviewQuestionsRepository;
import ai.qorva.core.dto.InterviewQuestionsDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.InterviewQuestionsMapper;
import ai.qorva.core.qbe.InterviewQuestionsQueryBuilder;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InterviewQuestionsService extends AbstractQorvaService<InterviewQuestionsDTO, InterviewQuestions> {

	@Autowired
	protected InterviewQuestionsService(InterviewQuestionsRepository repository, InterviewQuestionsMapper mapper, InterviewQuestionsQueryBuilder queryBuilder) {
		super(repository, mapper, queryBuilder);
	}

	@Override
	protected void preProcessCreateOne(InterviewQuestionsDTO dto) throws QorvaException {
		super.preProcessCreateOne(dto);

		if (!StringUtils.isEmpty(dto.getJobPostId())) {
			throw new QorvaException("Job post id can't be empty");
		}
	}
}
