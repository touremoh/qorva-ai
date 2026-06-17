package ai.qorva.core.controller;

import ai.qorva.core.dto.InsightConversationSummaryDTO;
import ai.qorva.core.dto.InsightConversationTurnDTO;
import ai.qorva.core.dto.InsightRequestDTO;
import ai.qorva.core.dto.InsightResponseDTO;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.InsightConversationService;
import ai.qorva.core.service.LibraryInsightsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/library-insights")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
@RequiredArgsConstructor
public class LibraryInsightsController {

	private final LibraryInsightsService libraryInsightsService;
	private final InsightConversationService conversationService;

	@PostMapping("/ask")
	//@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_LIBRARY_INSIGHTS')")
	public ResponseEntity<InsightResponseDTO> ask(@Valid @RequestBody InsightRequestDTO request) {
		String tenantId = TenantContextHolder.getTenantId();
		String userId = SecurityContextHolder.getContext().getAuthentication().getName();
		InsightResponseDTO response = libraryInsightsService.ask(request, tenantId, userId);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/conversations")
	//@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_LIBRARY_INSIGHTS')")
	public ResponseEntity<List<InsightConversationSummaryDTO>> getAllConversations() {
		String tenantId = TenantContextHolder.getTenantId();
		String userId = SecurityContextHolder.getContext().getAuthentication().getName();
		return ResponseEntity.ok(conversationService.getAllConversations(tenantId, userId));
	}

	@GetMapping("/conversations/{conversationId}")
	//@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_LIBRARY_INSIGHTS')")
	public ResponseEntity<List<InsightConversationTurnDTO>> getConversationHistory(@PathVariable String conversationId) {
		String tenantId = TenantContextHolder.getTenantId();
		String userId = SecurityContextHolder.getContext().getAuthentication().getName();
		List<InsightConversationTurnDTO> history = conversationService.getHistory(conversationId, tenantId, userId);
		return ResponseEntity.ok(history);
	}

	@DeleteMapping("/conversations/{conversationId}")
	//@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_LIBRARY_INSIGHTS')")
	public ResponseEntity<Void> deleteConversation(@PathVariable String conversationId) {
		String tenantId = TenantContextHolder.getTenantId();
		String userId = SecurityContextHolder.getContext().getAuthentication().getName();
		conversationService.deleteConversation(conversationId, tenantId, userId);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
