package ai.qorva.core.controller;

import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.request.AddUserRequest;
import ai.qorva.core.dto.request.UpdateAuthoritiesRequest;
import ai.qorva.core.dto.request.UpdatePasswordRequest;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class UserController extends AbstractQorvaController<UserDTO> {

    private final UserService userService;

    @Autowired
    public UserController(UserService service) {
        super(service);
        this.userService = service;
    }

    @PostMapping("/invite")
    @PreAuthorize("@accessManager.hasPermission(authentication,'MANAGE_USERS')")
    public ResponseEntity<UserDTO> inviteUser(@RequestBody @Valid AddUserRequest request) throws QorvaException {
        var created = userService.addUser(currentTenantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/authorities")
    @PreAuthorize("@accessManager.hasPermission(authentication,'MANAGE_USERS')")
    public ResponseEntity<Void> updateAuthorities(@PathVariable String id,
                                                  @RequestBody @Valid UpdateAuthoritiesRequest request) throws QorvaException {
        userService.updateAuthorities(currentTenantId(), id, request.getAuthorities());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> updatePassword(@PathVariable String id,
                                               @RequestBody @Valid UpdatePasswordRequest req) throws QorvaException {
        userService.updatePassword(currentTenantId(), id, req.getCurrentPassword(), req.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
