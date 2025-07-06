package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.AIScreeningService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class AIQueryController {

	private final AIScreeningService aiScreeningService;

	@Autowired
	public AIQueryController(AIScreeningService aiScreeningService) {
		this.aiScreeningService = aiScreeningService;
	}

	@GetMapping("/screen-resumes")
	public ResponseEntity<QorvaRequestResponse> performCVScreening(
		@RequestParam("jobPostId") String jobPostId,
		@RequestParam("tags") List<String> tags
	) throws QorvaException {
		return BuildApiResponse.from(this.aiScreeningService.startScreeningProcess(jobPostId, tags));
	}

	@GetMapping("/generate-interview-questions")
	public ResponseEntity<QorvaRequestResponse> generateInterviewQuestions(@RequestParam("jobPostId") String jobPostId) throws QorvaException {
		// TODO: change the call to this method
		// TODO: interview questions must be generated in the language of the job description
		//return BuildApiResponse.from(this.aiScreeningService.startScreeningProcess(jobPostId));
		return null;
	}
}
