package ai.qorva.core.admin.controller;

import ai.qorva.core.admin.dto.AdminPage;
import ai.qorva.core.admin.dto.AdminTenantData;
import ai.qorva.core.admin.dto.AdminTesterData;
import ai.qorva.core.admin.service.AdminTesterQueries;
import ai.qorva.core.admin.service.AdminTesterService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Test accounts: listed for every admin, managed by OWNER only. */
@RestController
@RequestMapping("/admin/testers")
public class AdminTesterController {

	private final AdminTesterQueries queries;
	private final AdminTesterService testers;

	public AdminTesterController(AdminTesterQueries queries, AdminTesterService testers) {
		this.queries = queries;
		this.testers = testers;
	}

	@GetMapping
	public AdminPage<AdminTesterData.Tester> list(@RequestParam Map<String, String> params) {
		return queries.list(params);
	}

	@GetMapping("/{tenantId}")
	public AdminTesterData.Tester get(@PathVariable String tenantId) throws QorvaException {
		return queries.get(tenantId);
	}

	@PostMapping
	@PreAuthorize(AdminRoles.OWNER)
	public ResponseEntity<AdminTesterData.Tester> create(@RequestBody AdminTesterData.CreateRequest request) throws QorvaException {
		return ResponseEntity.status(HttpStatus.CREATED).body(testers.create(request));
	}

	@PatchMapping("/{tenantId}/expiry")
	@PreAuthorize(AdminRoles.OWNER)
	public AdminTesterData.Tester expiry(@PathVariable String tenantId, @RequestBody AdminTesterData.ExpiryRequest request) throws QorvaException {
		return testers.changeExpiry(tenantId, request.accessExpiresAt());
	}

	@PatchMapping("/{tenantId}/tier")
	@PreAuthorize(AdminRoles.OWNER)
	public AdminTesterData.Tester tier(@PathVariable String tenantId, @RequestBody AdminTesterData.TierRequest request) throws QorvaException {
		return testers.changeTier(tenantId, request.productId());
	}

	@PostMapping("/{tenantId}/deactivate")
	@PreAuthorize(AdminRoles.OWNER)
	public AdminTesterData.Tester deactivate(@PathVariable String tenantId,
	                                        @RequestBody(required = false) AdminTenantData.ReasonRequest request) throws QorvaException {
		return testers.deactivate(tenantId, request != null ? request.reason() : null);
	}

	@PostMapping("/{tenantId}/reactivate")
	@PreAuthorize(AdminRoles.OWNER)
	public AdminTesterData.Tester reactivate(@PathVariable String tenantId, @RequestBody AdminTesterData.ReactivateRequest request) throws QorvaException {
		return testers.reactivate(tenantId, request);
	}

	@PostMapping("/{tenantId}/password")
	@PreAuthorize(AdminRoles.OWNER)
	public AdminTesterData.Tester password(@PathVariable String tenantId, @RequestBody AdminTesterData.PasswordRequest request) throws QorvaException {
		return testers.resetPassword(tenantId, request.password());
	}
}
