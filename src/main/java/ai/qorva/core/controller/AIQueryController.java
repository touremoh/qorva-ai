package ai.qorva.core.controller;

import ai.qorva.core.dto.ChatRequest;
import ai.qorva.core.dto.ChatResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.AIScreeningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class AIQueryController {

	private final AIScreeningService aiScreeningService;

	@Autowired
	public AIQueryController(AIScreeningService aiScreeningService) {
		this.aiScreeningService = aiScreeningService;
	}

	@PostMapping("/chat")
	@PreAuthorize("hasAnyAuthority('Professional', 'Enterprise', 'FREE_TRIAL_PERIOD_ACTIVE')")
	public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest chatRequest) throws QorvaException {
		throw new QorvaException("Not yet implemented");
	}
}
