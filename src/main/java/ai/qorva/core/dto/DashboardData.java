package ai.qorva.core.dto;

import lombok.Builder;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.List;

@Builder
public record DashboardData(
	String subscriptionStatus,
	long totalCVs,
	long totalJobsPosted,
	long totalUsers,
	long totalResumeAnalysis,
	long totalResumesProcessedCurrentMonth,
	List<SkillReport> skillsReport,
	List<ApplicationPerJobPostReport> jobPostsReport,
	List<TopCandidatesPerJobReport> topCandidatesPerJob
) {

	public record ApplicationPerJobPostReport(String jobPostTitle, int totalMatch) {}

	public record SkillReport(String skill, int totalMatch) {}

	public record TopCandidatesPerJobReport(
		String jobPostTitle,
		List<TopCandidate> topCandidates
	) {}


	public record TopCandidate(
		String candidateId,
		String candidateName,
		Integer score
	) {}
}
