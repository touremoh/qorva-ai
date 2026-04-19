package ai.qorva.core.controller;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dto.ClientDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.requests.ClientRequestMapper;
import ai.qorva.core.service.ClientService;
import ai.qorva.core.service.QorvaUserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/clients")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class ClientController extends AbstractQorvaController<ClientDTO> {

	@Autowired
	public ClientController(ClientService service, ClientRequestMapper requestMapper, QorvaUserDetailsService userService, JwtConfig jwtConfig) {
		super(service, requestMapper, userService, jwtConfig);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_CLIENT')")
	public ResponseEntity<QorvaRequestResponse> findOneById(String id) throws QorvaException {
		return super.findOneById(id);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_CLIENT')")
	public ResponseEntity<QorvaRequestResponse> findOneByData(String authorizationHeader, ClientDTO requestData) throws QorvaException {
		return super.findOneByData(authorizationHeader, requestData);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'ADD_CLIENT')")
	public ResponseEntity<QorvaRequestResponse> createOne(String authorizationHeader, ClientDTO data) throws QorvaException {
		return super.createOne(authorizationHeader, data);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_CLIENT')")
	public ResponseEntity<QorvaRequestResponse> findAll(String tenantId, int pageSize, int pageNumber) throws QorvaException {
		return super.findAll(tenantId, pageSize, pageNumber);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_CLIENT')")
	public ResponseEntity<QorvaRequestResponse> findAll(String authorizationHeader, Map<String, String> params) throws QorvaException {
		return super.findAll(authorizationHeader, params);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_CLIENT')")
	public ResponseEntity<QorvaRequestResponse> findManyByIds(List<String> ids) throws QorvaException {
		return super.findManyByIds(ids);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'MODIFY_CLIENT')")
	public ResponseEntity<QorvaRequestResponse> updateOne(String id, ClientDTO data) throws QorvaException {
		return super.updateOne(id, data);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'MODIFY_CLIENT')")
	public ResponseEntity<QorvaRequestResponse> patchOne(String id, ClientDTO data) throws QorvaException {
		return super.patchOne(id, data);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'DELETE_CLIENT')")
	public ResponseEntity<QorvaRequestResponse> deleteOneById(String id, String authorizationHeader) throws QorvaException {
		return super.deleteOneById(id, authorizationHeader);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_CLIENT')")
	public ResponseEntity<QorvaRequestResponse> existsByData(ClientDTO data) throws QorvaException {
		return super.existsByData(data);
	}
}
