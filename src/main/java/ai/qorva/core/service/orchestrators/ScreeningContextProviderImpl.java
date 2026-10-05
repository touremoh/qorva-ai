package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.dto.ScreeningContext;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.MatchingReportMapper;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.JobPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScreeningContextProviderImpl implements ScreeningContextProvider {

	private final CVService cvService;
	private final JobPostService jobpostService;
	private final MatchingReportRepository matchingReportRepository;
	private final MatchingReportMapper matchingReportMapper;
	private final ChatContextSerializer serializer;

	@Override
	public ScreeningContext load(String tenantId, String cvId, String jobPostId) throws QorvaException {
		CVDTO cv = cvService.findOneById(cvId);
		var cvText = serializer.serialize(cv);
		var jobPostText = serializer.serialize(jobpostService.findOneById(jobPostId));

		Optional<MatchingReportDTO> report = findReport(tenantId, cvId, jobPostId);
		if (report.isEmpty()) {
			return new ScreeningContext(cvText, jobPostText, null);
		}

		MatchingReportDTO dto = report.get();
		Double score = dto.getMatchingReportDetails() != null && dto.getMatchingReportDetails().getDecisionSummary() != null
			? dto.getMatchingReportDetails().getDecisionSummary().getFinalScore() : null;
		boolean stale = cv.getLastUpdatedAt() != null && dto.getLastUpdatedAt() != null
			&& cv.getLastUpdatedAt().isAfter(dto.getLastUpdatedAt());
		return new ScreeningContext(cvText, jobPostText, serializer.serialize(dto), dto.getId(), score, stale);
	}

	/** The pair's report, by (tenant, job, candidate) — the same lookup the report pages use. */
	private Optional<MatchingReportDTO> findReport(String tenantId, String cvId, String jobPostId) {
		if (!ObjectId.isValid(tenantId) || !ObjectId.isValid(jobPostId)) {
			return Optional.empty();
		}
		return matchingReportRepository
			.findOneByTenantIdAndJobPostIdAndCandidateInfoCandidateId(new ObjectId(tenantId), new ObjectId(jobPostId), cvId)
			.map(matchingReportMapper::map);
	}
}
