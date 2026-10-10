package ai.qorva.core.admin.controller;

import ai.qorva.core.admin.dto.AdminUserData;
import ai.qorva.core.admin.service.AdminTenantUserService;
import ai.qorva.core.exception.QorvaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Users of a company. SUPPORT may change a status, re-send an invite, sign out and reset MFA; the rest is OWNER only. */
@RestController
@RequestMapping("/admin/tenants/{tenantId}/users")
public class AdminTenantUserController {

	private final AdminTenantUserService users;

	public AdminTenantUserController(AdminTenantUserService users) {
		this.users = users;
	}

	@GetMapping
	public List<AdminUserData.TenantUser> list(@PathVariable String tenantId) throws QorvaException {
		return users.list(tenantId);
	}

	@PostMapping
	@PreAuthorize(AdminRoles.OWNER)
	public ResponseEntity<AdminUserData.TenantUser> invite(@PathVariable String tenantId, @RequestBody AdminUserData.InviteRequest request) throws QorvaException {
		return ResponseEntity.status(HttpStatus.CREATED).body(users.invite(tenantId, request));
	}

	@PatchMapping("/{userId}")
	@PreAuthorize(AdminRoles.OWNER)
	public AdminUserData.TenantUser rename(@PathVariable String tenantId, @PathVariable String userId,
	                                      @RequestBody AdminUserData.UpdateRequest request) throws QorvaException {
		return users.rename(tenantId, userId, request);
	}

	@PutMapping("/{userId}/authorities")
	@PreAuthorize(AdminRoles.OWNER)
	public AdminUserData.TenantUser authorities(@PathVariable String tenantId, @PathVariable String userId,
	                                           @RequestBody AdminUserData.AuthoritiesRequest request) throws QorvaException {
		return users.setAuthorities(tenantId, userId, request.actions());
	}

	@PostMapping("/{userId}/status")
	public AdminUserData.TenantUser status(@PathVariable String tenantId, @PathVariable String userId,
	                                      @RequestBody AdminUserData.StatusRequest request) throws QorvaException {
		return users.setStatus(tenantId, userId, request.status());
	}

	@PostMapping("/{userId}/invite/resend")
	public ResponseEntity<Void> resendInvite(@PathVariable String tenantId, @PathVariable String userId) throws QorvaException {
		users.resendInvite(tenantId, userId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{userId}/sign-out")
	public ResponseEntity<Void> signOut(@PathVariable String tenantId, @PathVariable String userId) throws QorvaException {
		users.signOut(tenantId, userId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{userId}/mfa/reset")
	public AdminUserData.TenantUser resetMfa(@PathVariable String tenantId, @PathVariable String userId) throws QorvaException {
		return users.resetMfa(tenantId, userId);
	}

	@DeleteMapping("/{userId}")
	@PreAuthorize(AdminRoles.OWNER)
	public ResponseEntity<Void> delete(@PathVariable String tenantId, @PathVariable String userId) throws QorvaException {
		users.delete(tenantId, userId);
		return ResponseEntity.noContent().build();
	}
}
