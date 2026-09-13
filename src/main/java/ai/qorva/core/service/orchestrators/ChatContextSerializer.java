package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.dto.common.CandidateInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

/**
 * Serialises the CV / job post / matching report for the resume-chat prompt. The job's
 * scoringRules stay in on purpose: they let the model explain the official screening
 * score in the tenant's own terms instead of inventing a score of its own. Unlike
 * {@code QorvaUtils.toJSON} it drops nulls, empties, identifiers, audit fields and the
 * search/clustering side-structures the model has no use for, and orders keys so the
 * output is byte-identical from one turn to the next — the context block is the leading
 * prefix of every prompt, and an identical prefix is what lets the provider cache it.
 */
@Component
public class ChatContextSerializer {

	@JsonIgnoreProperties({"id", "tenantId", "createdBy", "lastUpdatedBy", "createdAt", "lastUpdatedAt",
		"searchIndex", "candidateClustering", "attachment", "atsRefs", "qualityFlags", "archived",
		"contentDate", "contentDateSource", "applicantNumber", "languageCode"})
	private abstract static class CvMixin {}

	@JsonIgnoreProperties({"id", "tenantId", "createdBy", "lastUpdatedBy", "createdAt", "lastUpdatedAt",
		"matchingReportsNeeded", "atsRef", "status", "jobReference", "languageCode"})
	private abstract static class JobPostMixin {}

	@JsonIgnoreProperties({"id", "tenantId", "createdBy", "lastUpdatedBy", "createdAt", "lastUpdatedAt",
		"jobPostId", "status", "languageCode"})
	private abstract static class MatchingReportMixin {}

	@JsonIgnoreProperties({"candidateId", "candidateClustering"})
	private abstract static class CandidateInfoMixin {}

	private final ObjectMapper mapper = new ObjectMapper()
		.registerModule(new JavaTimeModule())
		.setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
		.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.addMixIn(CVDTO.class, CvMixin.class)
		.addMixIn(JobPostDTO.class, JobPostMixin.class)
		.addMixIn(MatchingReportDTO.class, MatchingReportMixin.class)
		.addMixIn(CandidateInfo.class, CandidateInfoMixin.class);

	public String serialize(Object dto) {
		if (dto == null) {
			return null;
		}
		try {
			return mapper.writeValueAsString(dto);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot serialise chat context", e);
		}
	}
}
