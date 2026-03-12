package ai.qorva.core.controller;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dto.ClientDTO;
import ai.qorva.core.mapper.requests.ClientRequestMapper;
import ai.qorva.core.service.ClientService;
import ai.qorva.core.service.QorvaUserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/clients")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class ClientController extends AbstractQorvaController<ClientDTO> {

	@Autowired
	public ClientController(ClientService service, ClientRequestMapper requestMapper, QorvaUserDetailsService userService, JwtConfig jwtConfig) {
		super(service, requestMapper, userService, jwtConfig);
	}
}
