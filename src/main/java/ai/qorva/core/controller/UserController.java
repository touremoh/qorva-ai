package ai.qorva.core.controller;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.mapper.requests.UserRequestMapper;
import ai.qorva.core.service.QorvaUserDetailsService;
import ai.qorva.core.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController extends AbstractQorvaController<UserDTO> {

    @Autowired
    public UserController(UserService service, UserRequestMapper requestMapper, QorvaUserDetailsService userService, JwtConfig jwtConfig) {
        super(service, requestMapper, userService, jwtConfig);
    }
}
