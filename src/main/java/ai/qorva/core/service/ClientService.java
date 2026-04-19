package ai.qorva.core.service;

import ai.qorva.core.dao.entity.Client;
import ai.qorva.core.dao.repository.ClientRepository;
import ai.qorva.core.dto.ClientDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.ClientMapper;
import ai.qorva.core.qbe.ClientQueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
public class ClientService extends AbstractQorvaService<ClientDTO, Client> {
	@Autowired
	protected ClientService(ClientRepository repository, ClientMapper mapper, ClientQueryBuilder queryBuilder) {
		super(repository, mapper, queryBuilder);
	}

	@Override
	protected void preProcessUpdateOne(String id, ClientDTO newClient) throws QorvaException {
		log.info("Updating client {}", id);

		// The client ID must not be null or empty
		if (Objects.isNull(id) || id.isEmpty()) {
			throw new QorvaException("Client ID cannot be null or empty");
		}

		// The client cannot be null
		if (Objects.isNull(newClient)) {
			throw new QorvaException("Client cannot be null");
		}

		// Get the client by id and update it
		var currentClient = this.findOneById(id);

		// Update client entity with data from dto
		mapper.merge(newClient, currentClient);
	}
}
