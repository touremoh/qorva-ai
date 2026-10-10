package ai.qorva.core.admin.controller;

import ai.qorva.core.admin.dto.AdminJobData;
import ai.qorva.core.admin.dto.AdminPage;
import ai.qorva.core.admin.dto.AdminTenantData;
import ai.qorva.core.admin.service.AdminTenantQueries;
import ai.qorva.core.admin.service.AdminTenantService;
import ai.qorva.core.exception.QorvaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Companies: read for every admin, change for OWNER only. */
@RestController
@RequestMapping("/admin/tenants")
public class AdminTenantController {

	private final AdminTenantQueries queries;
	private final AdminTenantService tenants;

	public AdminTenantController(AdminTenantQueries queries, AdminTenantService tenants) {
		this.queries = queries;
		this.tenants = tenants;
	}

	@GetMapping
	public AdminPage<AdminTenantData.TenantRow> list(@RequestParam Map<String, String> params) {
		return queries.list(params);
	}

	@GetMapping("/{id}")
	public AdminTenantData.TenantDetail get(@PathVariable String id) throws QorvaException {
		return queries.detail(id);
	}

	@PostMapping
	@PreAuthorize(AdminRoles.OWNER)
	public ResponseEntity<AdminTenantData.TenantDetail> createDemo(@RequestBody AdminTenantData.CreateDemoRequest request) throws QorvaException {
		return ResponseEntity.status(HttpStatus.CREATED).body(tenants.createDemo(request));
	}

	@PatchMapping("/{id}")
	@PreAuthorize(AdminRoles.OWNER)
	public AdminTenantData.TenantDetail update(@PathVariable String id, @RequestBody AdminTenantData.UpdateTenantRequest request) throws QorvaException {
		return tenants.updateProfile(id, request);
	}

	@PostMapping("/{id}/status")
	@PreAuthorize(AdminRoles.OWNER)
	public AdminTenantData.TenantDetail status(@PathVariable String id, @RequestBody AdminTenantData.StatusRequest request) throws QorvaException {
		return tenants.changeStatus(id, request);
	}

	@PostMapping("/{id}/resync-stripe")
	@PreAuthorize(AdminRoles.OWNER)
	public AdminTenantData.TenantDetail resyncStripe(@PathVariable String id) throws QorvaException {
		return tenants.resyncStripe(id);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize(AdminRoles.OWNER)
	public AdminTenantData.TenantDetail delete(@PathVariable String id,
	                                           @RequestBody(required = false) AdminTenantData.ReasonRequest request) throws QorvaException {
		return tenants.softDelete(id, request != null ? request.reason() : null);
	}

	@PostMapping("/{id}/restore")
	@PreAuthorize(AdminRoles.OWNER)
	public AdminTenantData.TenantDetail restore(@PathVariable String id) throws QorvaException {
		return tenants.restore(id);
	}

	@PostMapping("/{id}/purge")
	@PreAuthorize(AdminRoles.OWNER)
	public ResponseEntity<AdminJobData.JobRow> purge(@PathVariable String id, @RequestBody AdminTenantData.PurgeRequest request) throws QorvaException {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(tenants.purge(id, request.confirmName()));
	}
}
