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
	public ResumeMatchController(ResumeMatchService service) {
		super(service);
	}

	@GetMapping("/check/monthly-usage")
	public ResponseEntity<QorvaRequestResponse> checkCVAnalysisMonthlyUsageLimit() throws QorvaException {
		return BuildApiResponse.from(((ResumeMatchService) service).checkCVAnalysisMonthlyUsageLimit(currentTenantId()));
	}

	@GetMapping("/search")
	public ResponseEntity<QorvaRequestResponse> searchAll(
		@RequestParam("searchTerms") String searchTerms,
		@RequestParam("pageSize") int pageSize,
		@RequestParam("pageNumber") int pageNumber) throws QorvaException {
		return BuildApiResponse.from(((ResumeMatchService) this.service).searchAll(currentTenantId(), searchTerms, pageSize, pageNumber));
	}
}
