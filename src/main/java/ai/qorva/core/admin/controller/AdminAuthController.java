package ai.qorva.core.admin.controller;

import ai.qorva.core.admin.dto.AdminAuthData;
import ai.qorva.core.admin.service.AdminAccountService;
import ai.qorva.core.admin.service.AdminAuthService;
import ai.qorva.core.exception.QorvaException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin console sign-in and the signed-in admin's profile. Login, the code routes and set-password are public within the admin chain. */
@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

	private final AdminAuthService auth;
	private final AdminAccountService accounts;

	public AdminAuthController(AdminAuthService auth, AdminAccountService accounts) {
		this.auth = auth;
		this.accounts = accounts;
	}

	@PostMapping("/login")
	public AdminAuthData.Challenge login(@RequestBody AdminAuthData.LoginRequest request) throws QorvaException {
		return auth.login(request);
	}

	@PostMapping("/mfa/verify")
	public AdminAuthData.Session verify(@RequestBody AdminAuthData.VerifyRequest request) throws QorvaException {
		return auth.verify(request);
	}

	@PostMapping("/mfa/resend")
	public AdminAuthData.Challenge resend(@RequestBody AdminAuthData.ResendRequest request) throws QorvaException {
		return auth.resend(request);
	}

	@PostMapping("/refresh")
	public AdminAuthData.Session refresh() throws QorvaException {
		return auth.refresh();
	}

	@GetMapping("/me")
	public AdminAuthData.Admin me() throws QorvaException {
		return auth.me();
	}

	@PatchMapping("/me")
	public AdminAuthData.Admin updateMe(@RequestBody AdminAuthData.UpdateProfileRequest request) throws QorvaException {
		return accounts.updateProfile(request);
	}

	@PostMapping("/password/set")
	public ResponseEntity<Void> setPassword(@RequestBody AdminAuthData.SetPasswordRequest request) throws QorvaException {
		auth.setPassword(request);
		return ResponseEntity.noContent().build();
	}
}
