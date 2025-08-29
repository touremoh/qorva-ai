package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.ScreeningContext;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.ResumeMatchService;
import ai.qorva.core.utils.QorvaUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ScreeningContextProviderImpl implements ScreeningContextProvider {

	private final CVService cvService;
	private final ResumeMatchService resumeMatchService;
	private final JobPostService jobpostService;

	@Override
	public ScreeningContext load(String cvId, String jobPostId, String resumeMatchId) throws QorvaException {
		// Get the job post info
		var jobPostDTO = jobpostService.findOneById(jobPostId);

		// Get the CV info
		var cvDTO = cvService.findOneById(cvId);

		// Convert cv to JSON
		var cvText = QorvaUtils.toJSON(cvDTO);

		// Convert job post to JSON
		var jobPostText = QorvaUtils.toJSON(jobPostDTO);

		// Check if resume match id is provided
		String resumeMatchText = null;

		if (Objects.nonNull(resumeMatchId) && !resumeMatchId.isEmpty()) {
			// Get the resume match info
			var resumeMatchDTO = resumeMatchService.findOneById(resumeMatchId);

			// Convert resume match to JSON
			resumeMatchText = QorvaUtils.toJSON(resumeMatchDTO);
		}
		// Build the context
		return new ScreeningContext(jobPostText, cvText, resumeMatchText);
	}
}
