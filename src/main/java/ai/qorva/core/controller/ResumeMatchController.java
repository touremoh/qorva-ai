package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.ResumeMatchDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ResumeMatchService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/resume-matches")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class ResumeMatchController extends AbstractQorvaController<ResumeMatchDTO> {

	@Autowired
	public ResumeMatchController(ResumeMatchService resumeMatchService) {
		super(resumeMatchService);
	}

	@GetMapping("/check/monthly-usage/{tenantId}")
	public ResponseEntity<QorvaRequestResponse> checkCVAnalysisMonthlyUsageLimit(@PathVariable String tenantId) throws QorvaException {
		return BuildApiResponse.from(((ResumeMatchService)service).checkCVAnalysisMonthlyUsageLimit(tenantId));
	}
}
