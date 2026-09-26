package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dao.entity.Chat;
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
import org.springframework.util.StringUtils;

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
	public ScreeningContext load(Chat chat) throws QorvaException {
		var ctx = chat.getContext();
		CVDTO cv = cvService.findOneById(ctx.getCvId());
		var cvText = serializer.serialize(cv);
		var jobPostText = serializer.serialize(jobpostService.findOneById(ctx.getJobPostId()));

		Optional<MatchingReportDTO> report = findReport(chat);
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

	/** By id when the chat is linked to a report, otherwise by pair lookup (the same query the create dialog uses). */
	private Optional<MatchingReportDTO> findReport(Chat chat) {
		var ctx = chat.getContext();
		if (StringUtils.hasText(ctx.getMatchingReportId())) {
			return matchingReportRepository.findByIdInTenant(ctx.getMatchingReportId(), chat.getTenantId())
				.map(matchingReportMapper::map);
		}
		if (!ObjectId.isValid(chat.getTenantId()) || !ObjectId.isValid(ctx.getJobPostId())) {
			return Optional.empty();
		}
		return matchingReportRepository
			.findOneByTenantIdAndJobPostIdAndCandidateInfoCandidateId(new ObjectId(chat.getTenantId()), new ObjectId(ctx.getJobPostId()), ctx.getCvId())
			.map(matchingReportMapper::map);
	}
}
