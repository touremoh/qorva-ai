package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.dto.request.AddUserRequest;
import ai.qorva.core.dto.request.UpdateAuthoritiesRequest;
import ai.qorva.core.dto.request.UpdatePasswordRequest;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.CrudOperation;
import ai.qorva.core.security.CrudPolicy;
import ai.qorva.core.security.LanguageContextHolder;
import ai.qorva.core.service.UserService;
import ai.qorva.core.utils.BuildApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import static ai.qorva.core.security.CrudOperation.*;

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class UserController extends AbstractQorvaController<UserDTO> {

    private static final String MANAGE_USERS = "MANAGE_USERS";

    private final UserService userService;

    @Autowired
    public UserController(UserService service) {
        super(service);
        this.userService = service;
    }

    /*
     * Users are created through /invite and their authorities and passwords change through their own
     * guarded routes, so the generic create/search/ids/exists stay closed and the generic update is a
     * profile edit only (see updateProfile).
     */
    @Override
    protected CrudPolicy crudPolicy() {
        return CrudPolicy.builder()
            .allowAuthenticated(GET_ONE, LIST, UPDATE)
            .allow(MANAGE_USERS, DELETE)
            .build();
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

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> updateOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @PathVariable String id,
            @RequestBody UserDTO data) throws QorvaException {
        return updateProfile(language, id, data);
    }

    @Override
    @PatchMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> patchOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @PathVariable String id,
            @RequestBody UserDTO data) throws QorvaException {
        return updateProfile(language, id, data);
    }

    /**
     * Profile edit: only the names are copied from the payload, so authorities, credentials,
     * email, status and tenant can never be written through this route. A user edits their own
     * profile; editing someone else's needs MANAGE_USERS.
     */
    private ResponseEntity<QorvaRequestResponse> updateProfile(String language, String id, UserDTO data) throws QorvaException {
        authorize(CrudOperation.UPDATE);
        var target = userService.findOneById(id);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean self = authentication != null
            && target.getEmail() != null
            && target.getEmail().equalsIgnoreCase(authentication.getName());
        if (!self && !currentUserHas(MANAGE_USERS)) {
            throw new AccessDeniedException("Missing " + MANAGE_USERS + " to edit another user's profile");
        }

        var profile = new UserDTO();
        profile.setFirstName(data.getFirstName());
        profile.setLastName(data.getLastName());
        LanguageContextHolder.setLanguage(language);
        return BuildApiResponse.from(userService.updateOne(id, profile));
    }
}
