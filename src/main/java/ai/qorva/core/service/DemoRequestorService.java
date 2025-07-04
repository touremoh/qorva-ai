package ai.qorva.core.service;

import ai.qorva.core.dao.entity.DemoRequestor;
import ai.qorva.core.dao.repository.DemoRequestorRepository;
import ai.qorva.core.dto.DemoRequestorDTO;
import ai.qorva.core.mapper.DemoRequestorMapper;
import ai.qorva.core.qbe.DemoRequestorQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DemoRequestorService extends AbstractQorvaService<DemoRequestorDTO, DemoRequestor> {
	@Autowired
	protected DemoRequestorService(DemoRequestorRepository repository, DemoRequestorMapper mapper, DemoRequestorQueryBuilder queryBuilder) {
		super(repository, mapper, queryBuilder);
	}
}
