package ai.qorva.core.controller;

import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.request.UpdatePasswordRequest;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController extends AbstractQorvaController<UserDTO> {

    private final UserService userService;

    @Autowired
    public UserController(UserService service) {
        super(service);
        this.userService = service;
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> updatePassword(@PathVariable String id,
                                               @RequestBody @Valid UpdatePasswordRequest req) throws QorvaException {
        userService.updatePassword(currentTenantId(), id, req.getCurrentPassword(), req.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
