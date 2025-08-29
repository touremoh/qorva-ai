package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ApplicationPortalService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/portal")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class ApplicationPortalController {

	protected final ApplicationPortalService portalService;

	public ApplicationPortalController(ApplicationPortalService portalService) {
		this.portalService = portalService;
	}

	@GetMapping("/jobs/{jobId}/details")
	public ResponseEntity<QorvaRequestResponse> getJobPostDetails(@PathVariable String jobId) throws QorvaException {
		return BuildApiResponse.from(this.portalService.getJobPostDetails(jobId));
	}

	@PostMapping("/jobs/{jobId}/apply")
	public ResponseEntity<QorvaRequestResponse> apply(@PathVariable String jobId, @RequestParam("application") MultipartFile application) throws QorvaException {
		return BuildApiResponse.from(this.portalService.apply(jobId, application));
	}
}
