package ai.qorva.core.dto;

import lombok.Builder;

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
	List<ApplicationPerJobPostReport> jobPostsReport
) {

	public record ApplicationPerJobPostReport(String jobPostTitle, int totalMatch) {}
	public record SkillReport(String skill, int totalMatch) {}
}
