package ai.qorva.core.controller;

import ai.qorva.core.dto.AgentData;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.QorvaApiAccessManager;
import ai.qorva.core.service.agent.rules.AgentRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Copilot standing rules. Raw JSON like {@link AgentController}. Every route needs USE_AGENT; a rule is edited
 * and deleted by its owner only; the team's rules are listed, paused and resumed with MANAGE_USERS too.
 */
@RestController
@RequestMapping("/agent/rules")
@RequiredArgsConstructor
@CrossOrigin(origins = "${weblink.allowedOrigins}")
@PreAuthorize("@accessManager.hasPermission(authentication, 'USE_AGENT')")
public class AgentRuleController {

	private static final String TEAM = "team";

	private final AgentRuleService ruleService;
	private final QorvaApiAccessManager accessManager;

	private String currentTenantId() {
		return TenantContextHolder.getTenantId();
	}

	private String currentUsername() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	private boolean canManageTeam() {
		return accessManager.hasPermission(SecurityContextHolder.getContext().getAuthentication(),
			UserActionsEnum.MANAGE_USERS.getValue());
	}

	@GetMapping
	public ResponseEntity<List<AgentData.RuleView>> list(@RequestParam(defaultValue = "mine") String scope) throws QorvaException {
		boolean team = TEAM.equals(scope);
		if (team && !canManageTeam()) {
			throw QorvaErrors.forbidden(QorvaErrorCodes.AGENT_TEAM_SCOPE_FORBIDDEN);
		}
		return ResponseEntity.ok(ruleService.list(currentTenantId(), currentUsername(), team));
	}

	@PostMapping
	public ResponseEntity<AgentData.RuleView> create(
		@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
		@RequestBody AgentData.RuleRequest request) throws QorvaException {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ruleService.create(currentTenantId(), currentUsername(), language, request));
	}

	@GetMapping("/{id}")
	public ResponseEntity<AgentData.RuleView> get(@PathVariable String id) throws QorvaException {
		return ResponseEntity.ok(ruleService.get(currentTenantId(), currentUsername(), canManageTeam(), id));
	}

	@PutMapping("/{id}")
	public ResponseEntity<AgentData.RuleView> update(@PathVariable String id, @RequestBody AgentData.RuleRequest request)
		throws QorvaException {
		return ResponseEntity.ok(ruleService.update(currentTenantId(), currentUsername(), id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) throws QorvaException {
		ruleService.delete(currentTenantId(), currentUsername(), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/pause")
	public ResponseEntity<AgentData.RuleView> pause(@PathVariable String id) throws QorvaException {
		return ResponseEntity.ok(ruleService.pause(currentTenantId(), currentUsername(), canManageTeam(), id));
	}

	@PostMapping("/{id}/resume")
	public ResponseEntity<AgentData.RuleView> resume(@PathVariable String id) throws QorvaException {
		return ResponseEntity.ok(ruleService.resume(currentTenantId(), currentUsername(), canManageTeam(), id));
	}
}
