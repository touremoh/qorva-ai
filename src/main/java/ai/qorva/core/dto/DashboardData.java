package ai.qorva.core.dto;

import lombok.Builder;
import org.springframework.data.annotation.Id;

import java.util.List;

@Builder
public record DashboardData(
	String subscriptionStatus,
	long totalCVs,
	long totalJobsPosted,
	long totalUsers,
	long totalResumeAnalysis,
	List<SkillReport> skillsReport,
	List<ApplicationPerJobPostReport> jobPostsReport,
	List<ClusteringCategoryReport> skillDepthReport,
	List<ClusteringCategoryReport> seniorityLevelReport,
	List<ClusteringCategoryReport> leadershipReport,
	List<ClusteringCategoryReport> learningVelocityReport
) {

	public record ClusteringCategoryReport(String name, int count, double percentage) {}

	public record JobPostCount(long total) {}

	/** {@code jobPostId} is read from the aggregation's {@code _id} (group key). */
	public record ApplicationPerJobPostReport(@Id String jobPostId, String jobPostTitle, int totalMatch) {}

	public record SkillReport(String skill, int totalMatch) {}

	/** {@code jobPostId} is read from the aggregation's {@code _id} (group key). */
	public record TopCandidatesPerJobReport(
		@Id String jobPostId,
		String jobPostTitle,
		List<TopCandidate> topCandidates
	) {}

	public record TopCandidate(
		String candidateId,
		String candidateName,
		Integer score
	) {}

	public record TopCandidatesPage(
		List<TopCandidatesPerJobReport> content,
		int pageNumber,
		int pageSize,
		long totalElements,
		int totalPages,
		boolean hasNext
	) {}
}
