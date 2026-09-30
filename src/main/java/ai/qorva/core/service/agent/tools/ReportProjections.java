package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dto.common.CandidateInfo;
import ai.qorva.core.dto.common.MatchingReportDetails;

import java.util.LinkedHashMap;
import java.util.Map;

final class ReportProjections {

	private ReportProjections() {
	}

	static Double score(MatchingReportDetails details) {
		return details != null && details.getDecisionSummary() != null ? details.getDecisionSummary().getFinalScore() : null;
	}

	static Map<String, Object> summary(String reportId, String jobId, String jobTitle, CandidateInfo candidate, MatchingReportDetails details) {
		var out = new LinkedHashMap<String, Object>();
		out.put("reportId", reportId);
		out.put("jobId", jobId);
		out.put("jobTitle", jobTitle);
		out.put("cvId", candidate != null ? candidate.getCandidateId() : null);
		out.put("candidateName", candidate != null ? candidate.getCandidateName() : null);
		out.put("score", score(details));
		var decision = details != null ? details.getDecisionSummary() : null;
		out.put("verdict", decision != null ? decision.getShortVerdict() : null);
		out.put("recommendation", decision != null ? decision.getRecommendation() : null);
		return out;
	}
}
