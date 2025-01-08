package ai.qorva.core.service;

import ai.qorva.core.dao.entity.ScreeningReportDetails;
import ai.qorva.core.dao.repository.ScreeningReportDetailsRepository;
import ai.qorva.core.dto.ScreeningReportDetailsDTO;
import ai.qorva.core.mapper.ScreeningReportDetailsMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ScreeningReportDetailsService extends AbstractQorvaService<ScreeningReportDetailsDTO, ScreeningReportDetails> {

	@Autowired
	protected ScreeningReportDetailsService(ScreeningReportDetailsRepository repository, ScreeningReportDetailsMapper mapper) {
		super(repository, mapper);
	}
}
