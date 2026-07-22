package ai.qorva.core.controller;

import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.AIScreeningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class AIScreeningController {

	private final AIScreeningService aiScreeningService;

	@Autowired
	public AIScreeningController(AIScreeningService aiScreeningService) {
		this.aiScreeningService = aiScreeningService;
	}

	@PostMapping("/start-screening")
	@PreAuthorize("@accessManager.hasPermission(authentication,'GENERATE_REPORT') and @accessManager.hasNotExceededScreeningLimit()")
	public ResponseEntity<Void> startScreening(
		@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language
	) throws QorvaException {
		aiScreeningService.startScreeningProcess(TenantContextHolder.getTenantId(), language);
		return ResponseEntity.ok().build();
	}
}
