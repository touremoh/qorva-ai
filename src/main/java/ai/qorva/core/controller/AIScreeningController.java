package ai.qorva.core.controller;

import ai.qorva.core.dto.MatchingRunData;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.MatchingRunService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Matching runs: the recruiter picks open jobs and a Top N, sees the cost ({@code /estimate}), starts the run,
 * and follows it while a background worker does the work. GENERATE_REPORT to estimate, start or cancel;
 * VIEW_REPORT to follow; the plan's Top N choices to anyone who sees jobs or reports.
 */
@RestController
@RequestMapping("/ai")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class AIScreeningController {

	private final MatchingRunService matchingRunService;

	@Autowired
	public AIScreeningController(MatchingRunService matchingRunService) {
		this.matchingRunService = matchingRunService;
	}

	/**
	 * @deprecated the one-click "match every flagged job" of earlier app versions, kept for one release: it now
	 * queues a run for those jobs at the plan's default Top N and returns at once.
	 */
	@Deprecated(forRemoval = true)
	@PostMapping("/start-screening")
	@PreAuthorize("@accessManager.hasPermission(authentication,'GENERATE_REPORT') and @accessManager.hasNotExceededScreeningLimit()")
	public ResponseEntity<Void> startScreening(
		@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
		@AuthenticationPrincipal UserDetails userDetails
	) throws QorvaException {
		matchingRunService.submitFlagged(TenantContextHolder.getTenantId(), username(userDetails), language);
		return ResponseEntity.ok().build();
	}

	/** The plan's Top N choices — harmless, so anyone who sees jobs or reports may read them. */
	@GetMapping(path = "/matching-runs/options", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_REPORT') or @accessManager.hasPermission(authentication,'VIEW_JOB')")
	public ResponseEntity<MatchingRunData.Options> options() {
		return ResponseEntity.ok(matchingRunService.options(TenantContextHolder.getTenantId()));
	}

	@PostMapping(path = "/matching-runs/estimate", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'GENERATE_REPORT')")
	public ResponseEntity<MatchingRunData.Estimate> estimate(
		@RequestBody MatchingRunData.Request request,
		@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language
	) throws QorvaException {
		return ResponseEntity.ok(matchingRunService.estimate(TenantContextHolder.getTenantId(), request, language));
	}

	@PostMapping(path = "/matching-runs", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'GENERATE_REPORT')")
	public ResponseEntity<MatchingRunData.SubmitResponse> submit(
		@RequestBody MatchingRunData.Request request,
		@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
		@AuthenticationPrincipal UserDetails userDetails
	) throws QorvaException {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(matchingRunService.submit(TenantContextHolder.getTenantId(), username(userDetails), request, language));
	}

	/** {@code active=true}: runs still queued or running; otherwise the latest runs. */
	@GetMapping(path = "/matching-runs", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_REPORT')")
	public ResponseEntity<MatchingRunData.RunList> list(@RequestParam(defaultValue = "false") boolean active) {
		var tenantId = TenantContextHolder.getTenantId();
		return ResponseEntity.ok(active ? matchingRunService.active(tenantId) : matchingRunService.recent(tenantId));
	}

	@GetMapping(path = "/matching-runs/{runId}", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_REPORT')")
	public ResponseEntity<MatchingRunData.RunView> get(@PathVariable String runId) throws QorvaException {
		return ResponseEntity.ok(matchingRunService.get(TenantContextHolder.getTenantId(), runId));
	}

	@PostMapping(path = "/matching-runs/{runId}/cancel", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'GENERATE_REPORT')")
	public ResponseEntity<MatchingRunData.RunView> cancel(@PathVariable String runId) throws QorvaException {
		return ResponseEntity.ok(matchingRunService.cancel(TenantContextHolder.getTenantId(), runId));
	}

	private static String username(UserDetails userDetails) {
		return userDetails != null ? userDetails.getUsername() : null;
	}
}
