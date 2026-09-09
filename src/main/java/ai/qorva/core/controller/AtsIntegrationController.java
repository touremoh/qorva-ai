package ai.qorva.core.controller;

import ai.qorva.core.dto.AtsIntegrationData;
import ai.qorva.core.dto.BackgroundJobData;
import ai.qorva.core.enums.AtsProviderEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.ats.AtsConnectionService;
import ai.qorva.core.service.ats.AtsOauthService;
import ai.qorva.core.service.ats.AtsSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Tenant-facing ATS integration management. Every route requires the
 * MANAGE_INTEGRATIONS permission; connection ownership is always re-checked against
 * the tenant from the JWT.
 */
@RestController
@RequestMapping("/ats")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class AtsIntegrationController {

	private final AtsConnectionService connectionService;
	private final AtsSyncService syncService;
	private final AtsOauthService oauthService;

	@Autowired
	public AtsIntegrationController(AtsConnectionService connectionService, AtsSyncService syncService,
		AtsOauthService oauthService) {
		this.connectionService = connectionService;
		this.syncService = syncService;
		this.oauthService = oauthService;
	}

	@GetMapping(path = "/providers", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'MANAGE_INTEGRATIONS')")
	public ResponseEntity<AtsIntegrationData.ProviderCatalog> providers() {
		return ResponseEntity.ok(connectionService.catalog(TenantContextHolder.getTenantId()));
	}

	@GetMapping(path = "/connections", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'MANAGE_INTEGRATIONS')")
	public ResponseEntity<AtsIntegrationData.ConnectionList> list() {
		return ResponseEntity.ok(connectionService.list(TenantContextHolder.getTenantId()));
	}

	@PostMapping(path = "/connections", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'MANAGE_INTEGRATIONS')")
	public ResponseEntity<AtsIntegrationData.ConnectionView> create(
		@RequestBody AtsIntegrationData.CreateRequest request,
		@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		return ResponseEntity.status(HttpStatus.CREATED).body(connectionService.create(
			TenantContextHolder.getTenantId(), request,
			userDetails != null ? userDetails.getUsername() : null));
	}

	@PatchMapping(path = "/connections/{id}", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'MANAGE_INTEGRATIONS')")
	public ResponseEntity<AtsIntegrationData.ConnectionView> update(
		@PathVariable String id,
		@RequestBody AtsIntegrationData.UpdateRequest request) throws QorvaException {
		return ResponseEntity.ok(connectionService.update(TenantContextHolder.getTenantId(), id, request));
	}

	@DeleteMapping(path = "/connections/{id}")
	@PreAuthorize("@accessManager.hasPermission(authentication,'MANAGE_INTEGRATIONS')")
	public ResponseEntity<Void> delete(@PathVariable String id) throws QorvaException {
		connectionService.delete(TenantContextHolder.getTenantId(), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(path = "/connections/{id}/test", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'MANAGE_INTEGRATIONS')")
	public ResponseEntity<AtsIntegrationData.ConnectionView> test(@PathVariable String id) throws QorvaException {
		return ResponseEntity.ok(connectionService.test(TenantContextHolder.getTenantId(), id));
	}

	@PostMapping(path = "/connections/{id}/sync", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'MANAGE_INTEGRATIONS')")
	public ResponseEntity<BackgroundJobData.JobView> sync(
		@PathVariable String id,
		@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		return ResponseEntity.accepted().body(syncService.enqueue(
			TenantContextHolder.getTenantId(), id, AtsSyncService.TRIGGER_MANUAL,
			userDetails != null ? userDetails.getUsername() : null));
	}

	@GetMapping(path = "/connections/{id}/runs", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'MANAGE_INTEGRATIONS')")
	public ResponseEntity<BackgroundJobData.JobList> runs(@PathVariable String id) throws QorvaException {
		return ResponseEntity.ok(syncService.listRuns(TenantContextHolder.getTenantId(), id));
	}

	/**
	 * Retry automatic webhook registration after a failure — a provider outage, or an API key
	 * that was missing a webhook permission until the tenant fixed it.
	 */
	@PostMapping(path = "/connections/{id}/webhooks", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'MANAGE_INTEGRATIONS')")
	public ResponseEntity<AtsIntegrationData.ConnectionView> registerWebhooks(@PathVariable String id)
		throws QorvaException {
		return ResponseEntity.ok(connectionService.registerWebhooks(TenantContextHolder.getTenantId(), id));
	}

	@PostMapping(path = "/connections/oauth/start", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'MANAGE_INTEGRATIONS')")
	public ResponseEntity<AtsIntegrationData.OauthStartResponse> oauthStart(
		@RequestBody AtsIntegrationData.OauthStartRequest request) throws QorvaException {
		var provider = AtsProviderEnum.fromValue(request.provider());
		var url = oauthService.buildConsentUrl(provider, TenantContextHolder.getTenantId(), request.region());
		return ResponseEntity.ok(new AtsIntegrationData.OauthStartResponse(url));
	}
}
