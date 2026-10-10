package ai.qorva.core.admin.controller;

import ai.qorva.core.admin.dto.AdminAuthData;
import ai.qorva.core.admin.service.AdminAccountService;
import ai.qorva.core.exception.QorvaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Managing the admins themselves — OWNER only. */
@RestController
@RequestMapping("/admin/admins")
@PreAuthorize(AdminRoles.OWNER)
public class AdminAdminsController {

	private final AdminAccountService accounts;

	public AdminAdminsController(AdminAccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping
	public List<AdminAuthData.Admin> list() {
		return accounts.list();
	}

	@PostMapping
	public ResponseEntity<AdminAuthData.Admin> create(@RequestBody AdminAuthData.CreateAdminRequest request) throws QorvaException {
		return ResponseEntity.status(HttpStatus.CREATED).body(accounts.create(request));
	}

	@PatchMapping("/{id}")
	public AdminAuthData.Admin update(@PathVariable String id, @RequestBody AdminAuthData.UpdateAdminRequest request) throws QorvaException {
		return accounts.update(id, request);
	}

	@PostMapping("/{id}/invite/resend")
	public ResponseEntity<Void> resendInvite(@PathVariable String id) throws QorvaException {
		accounts.resendInvite(id);
		return ResponseEntity.noContent().build();
	}
}
