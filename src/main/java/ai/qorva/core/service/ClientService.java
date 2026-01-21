package ai.qorva.core.service;

import ai.qorva.core.dao.entity.Client;
import ai.qorva.core.dao.repository.ClientRepository;
import ai.qorva.core.dto.ClientDTO;
import ai.qorva.core.mapper.ClientMapper;
import ai.qorva.core.qbe.ClientQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ClientService extends AbstractQorvaService<ClientDTO, Client> {
	@Autowired
	protected ClientService(ClientRepository repository, ClientMapper mapper, ClientQueryBuilder queryBuilder) {
		super(repository, mapper, queryBuilder);
	}
}
