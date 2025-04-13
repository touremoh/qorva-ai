package ai.qorva.core.controller;

import ai.qorva.core.dto.JobApplicationDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.JobsApplicationService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/applications")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class JobsApplicationsController extends AbstractQorvaController<JobApplicationDTO> {

	@Autowired
	public JobsApplicationsController(JobsApplicationService jobsApplicationService) {
		super(jobsApplicationService);
	}

	@GetMapping("/check/monthly-usage/{userId}")
	public ResponseEntity<QorvaRequestResponse> checkCVAnalysisMonthlyUsageLimit(@PathVariable String userId) throws QorvaException {
		return BuildApiResponse.from(((JobsApplicationService)service).checkCVAnalysisMonthlyUsageLimit(userId));
	}
}
