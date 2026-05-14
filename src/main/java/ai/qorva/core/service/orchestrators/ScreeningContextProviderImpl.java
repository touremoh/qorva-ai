package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.ScreeningContext;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.MatchingReportService;
import ai.qorva.core.utils.QorvaUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ScreeningContextProviderImpl implements ScreeningContextProvider {

	private final CVService cvService;
	private final MatchingReportService matchingReportService;
	private final JobPostService jobpostService;

	@Override
	public ScreeningContext load(String cvId, String jobPostId, String matchingReportId) throws QorvaException {
		// Get the job post info
		var jobPostDTO = jobpostService.findOneById(jobPostId);

		// Get the CV info
		var cvDTO = cvService.findOneById(cvId);

		// Convert cv to JSON
		var cvText = QorvaUtils.toJSON(cvDTO);

		// Convert job post to JSON
		var jobPostText = QorvaUtils.toJSON(jobPostDTO);

		// Check if resume match id is provided
		String matchingReportText = null;

		if (Objects.nonNull(matchingReportId) && !matchingReportId.isEmpty()) {
			// Get the resume match info
			var matchingReportDTO = matchingReportService.findOneById(matchingReportId);

			// Convert resume match to JSON
			matchingReportText = QorvaUtils.toJSON(matchingReportDTO);
		}
		// Build the context
		return new ScreeningContext(jobPostText, cvText, matchingReportText);
	}
}
