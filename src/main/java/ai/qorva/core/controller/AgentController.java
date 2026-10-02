package ai.qorva.core.controller;

import ai.qorva.core.dto.AgentData;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.QorvaApiAccessManager;
import ai.qorva.core.service.agent.AgentRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Copilot. Raw JSON (no QorvaRequestResponse envelope), like the other newer feature controllers.
 * Every route needs USE_AGENT; the team scope additionally needs MANAGE_USERS.
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@CrossOrigin(origins = "${weblink.allowedOrigins}")
@PreAuthorize("@accessManager.hasPermission(authentication, 'USE_AGENT')")
public class AgentController {

	private static final String TEAM = "team";

	private final AgentRunService agentRunService;
	private final QorvaApiAccessManager accessManager;

	private String currentTenantId() {
		return TenantContextHolder.getTenantId();
	}

	private String currentUsername() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	private boolean canViewTeam() {
		return accessManager.hasPermission(SecurityContextHolder.getContext().getAuthentication(),
			UserActionsEnum.MANAGE_USERS.getValue());
	}

	@GetMapping("/availability")
	public ResponseEntity<AgentData.Availability> availability() {
		return ResponseEntity.ok(agentRunService.availability(currentTenantId(), canViewTeam()));
	}

	@PostMapping("/runs")
	public ResponseEntity<AgentData.RunView> start(
		@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
		@RequestBody AgentData.StartRunRequest request) throws QorvaException {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(agentRunService.start(currentTenantId(), currentUsername(), language, request));
	}

	@GetMapping("/runs")
	public ResponseEntity<AgentData.RunPage> list(
		@RequestParam(defaultValue = "mine") String scope,
		@RequestParam(required = false) String status,
		@RequestParam(required = false) String origin,
		@RequestParam(required = false) String ruleId,
		@RequestParam(required = false) String userEmail,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size) throws QorvaException {
		boolean team = TEAM.equals(scope);
		if (team && !canViewTeam()) {
			throw QorvaErrors.forbidden(QorvaErrorCodes.AGENT_TEAM_SCOPE_FORBIDDEN);
		}
		return ResponseEntity.ok(agentRunService.list(currentTenantId(), currentUsername(), team, status, origin,
			ruleId, userEmail, page, size));
	}

	@GetMapping("/runs/pending-approval/count")
	public ResponseEntity<AgentData.Count> pendingApprovalCount() {
		return ResponseEntity.ok(agentRunService.pendingApprovalCount(currentTenantId(), currentUsername()));
	}

	@GetMapping("/runs/{id}")
	public ResponseEntity<AgentData.RunView> get(@PathVariable String id) throws QorvaException {
		return ResponseEntity.ok(agentRunService.get(currentTenantId(), currentUsername(), canViewTeam(), id));
	}

	@PostMapping("/runs/{id}/cancel")
	public ResponseEntity<AgentData.RunView> cancel(@PathVariable String id) throws QorvaException {
		return ResponseEntity.ok(agentRunService.cancel(currentTenantId(), currentUsername(), canViewTeam(), id));
	}

	@PostMapping("/runs/{id}/actions/{actionId}/approve")
	public ResponseEntity<AgentData.RunView> approve(@PathVariable String id, @PathVariable String actionId,
	                                                 @RequestBody AgentData.DecisionRequest request) throws QorvaException {
		return ResponseEntity.ok(agentRunService.approve(currentTenantId(), currentUsername(), id, actionId, request));
	}

	@PostMapping("/runs/{id}/actions/{actionId}/reject")
	public ResponseEntity<AgentData.RunView> reject(@PathVariable String id, @PathVariable String actionId,
	                                                @RequestBody AgentData.DecisionRequest request) throws QorvaException {
		return ResponseEntity.ok(agentRunService.reject(currentTenantId(), currentUsername(), id, actionId, request));
	}

	@GetMapping("/conversations")
	public ResponseEntity<List<AgentData.ConversationSummary>> conversations() {
		return ResponseEntity.ok(agentRunService.conversations(currentTenantId(), currentUsername()));
	}

	@GetMapping("/conversations/{conversationId}")
	public ResponseEntity<List<AgentData.RunView>> conversation(@PathVariable String conversationId) {
		return ResponseEntity.ok(agentRunService.conversation(currentTenantId(), currentUsername(), conversationId));
	}

	@DeleteMapping("/conversations/{conversationId}")
	public ResponseEntity<Void> deleteConversation(@PathVariable String conversationId) throws QorvaException {
		agentRunService.deleteConversation(currentTenantId(), currentUsername(), conversationId);
		return ResponseEntity.noContent().build();
	}
}
