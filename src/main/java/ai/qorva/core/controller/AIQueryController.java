package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.AIScreeningService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@CrossOrigin(origins = "${weblink.allowedOrigin}")
public class AIQueryController {
	private final AIScreeningService aiScreeningService;

	@Autowired
	public AIQueryController(AIScreeningService aiScreeningService) {
		this.aiScreeningService = aiScreeningService;
	}

	@GetMapping("/screening-process")
	public ResponseEntity<QorvaRequestResponse> performCVScreening(
		@RequestParam("userId") String userId,
		@RequestParam("jobPostId") String jobPostId) throws QorvaException {
		return BuildApiResponse.from(this.aiScreeningService.startScreeningProcess(jobPostId, userId));
	}

	@GetMapping("/interview-questions")
	public ResponseEntity<QorvaRequestResponse> generateInterviewQuestions(
		@RequestParam("userId") String userId,
		@RequestParam("jobPostId") String jobPostId,
		@RequestParam("candidateId") String candidateId

	) throws QorvaException {
		// TODO: change the call to this method
		// TODO: interview questions must be generated in the language of the job description
		return BuildApiResponse.from(this.aiScreeningService.startScreeningProcess(jobPostId, userId));
	}
}
