package ai.qorva.core.service.agent.rules;

import ai.qorva.core.dao.entity.AgentRule;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.entity.MatchingReport;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Candidates scored on a job (or any job) at or above a score and/or recommended for an interview. A report
 * is re-written on every re-score, so the ledger key is the CV–job pair: each pair fires once per rule.
 */
@Component
public class CvScoredSource implements AgentTriggerSource {

	/** Report verdicts that mean "worth meeting" (Matching_report_response_format.json). */
	static final List<String> INTERVIEW = List.of("strong_interview", "interview");
	private static final String SUMMARY = "matchingReportDetails.decisionSummary.";

	private final MongoTemplate mongoTemplate;

	public CvScoredSource(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public String type() {
		return AgentRule.TRIGGER_CV_SCORED;
	}

	@Override
	public List<RuleSubject> newSubjects(AgentRule rule, Instant since, Instant now, int limit) {
		var trigger = rule.getTrigger();
		var criteria = Criteria.where("tenantId").is(new ObjectId(rule.getTenantId()))
			.and("lastUpdatedAt").gte(since).lte(now);
		if (trigger.getJobPostId() != null) {
			criteria = criteria.and("jobPostId").is(new ObjectId(trigger.getJobPostId()));
		}
		if (trigger.getMinScore() != null && trigger.getMaxScore() != null) {
			criteria = criteria.and(SUMMARY + "finalScore").gte(trigger.getMinScore()).lte(trigger.getMaxScore());
		} else if (trigger.getMinScore() != null) {
			criteria = criteria.and(SUMMARY + "finalScore").gte(trigger.getMinScore());
		} else if (trigger.getMaxScore() != null) {
			criteria = criteria.and(SUMMARY + "finalScore").lte(trigger.getMaxScore());
		}
		if (trigger.getRecommendations() != null) {
			criteria = criteria.and(SUMMARY + "recommendation").in(trigger.getRecommendations());
		} else if (Boolean.TRUE.equals(trigger.getRecommendedOnly())) {
			criteria = criteria.and(SUMMARY + "recommendation").in(INTERVIEW);
		}
		var query = Query.query(criteria).with(Sort.by("lastUpdatedAt")).limit(limit);
		query.fields().include("jobPostId", "jobPostTitle", "candidateInfo.candidateId", "candidateInfo.candidateName",
			SUMMARY + "finalScore", SUMMARY + "recommendation", "lastUpdatedAt", "tenantId");
		return mongoTemplate.find(query, MatchingReport.class).stream()
			.filter(r -> r.getCandidateInfo() != null && r.getCandidateInfo().getCandidateId() != null)
			.map(r -> subject(r, trigger.getRecommendations() != null))
			.toList();
	}

	/**
	 * {@code byVerdict}: a rule that filters on verdicts fires again when a re-score changes the verdict (key
	 * {@code cvId:jobId:verdict}); other rules fire once per CV–job pair, as they always have.
	 */
	static RuleSubject subject(MatchingReport report, boolean byVerdict) {
		var cvId = report.getCandidateInfo().getCandidateId();
		var name = RuleText.name(report.getCandidateInfo().getCandidateName());
		var job = RuleText.name(report.getJobPostTitle());
		var mentions = new ArrayList<AgentRun.Mention>();
		mentions.add(new AgentRun.Mention("CV", cvId, name));
		mentions.add(new AgentRun.Mention("JOB", report.getJobPostId(), job));
		var line = new StringBuilder("candidate ").append(name).append(" (cvId=").append(cvId).append(") for job ")
			.append(job).append(" (jobId=").append(report.getJobPostId()).append(')');
		var summary = report.getMatchingReportDetails() != null ? report.getMatchingReportDetails().getDecisionSummary() : null;
		if (summary != null && summary.getFinalScore() != null) {
			line.append(", score ").append(String.format(Locale.ROOT, "%.0f", summary.getFinalScore()));
		}
		if (summary != null && summary.getRecommendation() != null) {
			line.append(", recommendation ").append(summary.getRecommendation());
		}
		var key = cvId + ":" + report.getJobPostId();
		if (byVerdict && summary != null && summary.getRecommendation() != null) {
			key += ":" + summary.getRecommendation();
		}
		return new RuleSubject(key, report.getLastUpdatedAt(), mentions, line.toString());
	}
}
