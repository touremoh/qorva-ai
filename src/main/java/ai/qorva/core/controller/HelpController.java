package ai.qorva.core.controller;

import ai.qorva.core.dto.HelpData;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.help.HelpAssistantService;
import ai.qorva.core.service.help.SupportTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Qorva Help (product help assistant) and support requests. Raw JSON. Every signed-in user may use it — no
 * permission beyond authentication (the default for routes outside SecurityConfig's public list).
 */
@RestController
@RequestMapping("/help")
@RequiredArgsConstructor
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class HelpController {

	private final HelpAssistantService helpAssistantService;
	private final SupportTicketService supportTicketService;

	private String currentTenantId() {
		return TenantContextHolder.getTenantId();
	}

	private String currentUsername() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	@GetMapping("/availability")
	public ResponseEntity<HelpData.Availability> availability() {
		return ResponseEntity.ok(helpAssistantService.availability());
	}

	@PostMapping("/messages")
	public ResponseEntity<HelpData.Answer> ask(
		@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
		@RequestBody HelpData.AskRequest request) throws QorvaException {
		return ResponseEntity.ok(helpAssistantService.ask(currentTenantId(), currentUsername(), language, request));
	}

	@PostMapping("/tickets")
	public ResponseEntity<HelpData.TicketCreated> createTicket(
		@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
		@RequestBody HelpData.TicketRequest request) throws QorvaException {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(supportTicketService.create(currentTenantId(), currentUsername(), language, request));
	}
}
