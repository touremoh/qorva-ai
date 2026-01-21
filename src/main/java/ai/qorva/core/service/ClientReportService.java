package ai.qorva.core.service;

import ai.qorva.core.dao.entity.ClientReport;
import ai.qorva.core.dao.repository.ClientReportRepository;
import ai.qorva.core.dto.ClientReportDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.ClientReportMapper;
import ai.qorva.core.qbe.ClientReportQueryBuilder;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ClientReportService extends AbstractQorvaService<ClientReportDTO, ClientReport> {

	@Autowired
	protected ClientReportService(ClientReportRepository repository, ClientReportMapper mapper, ClientReportQueryBuilder queryBuilder) {
		super(repository, mapper, queryBuilder);
	}

	@Override
	protected void preProcessCreateOne(ClientReportDTO dto) throws QorvaException {
		super.preProcessCreateOne(dto);

		if (!StringUtils.isEmpty(dto.getClientId())) {
			throw new QorvaException("Client id can't be empty for client report");
		}
	}
}
