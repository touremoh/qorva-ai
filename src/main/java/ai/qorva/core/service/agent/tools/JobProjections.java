package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dto.JobPostDTO;
import org.jsoup.Jsoup;

import java.util.LinkedHashMap;
import java.util.Map;

final class JobProjections {

	private JobProjections() {
	}

	static Map<String, Object> summary(JobPostDTO job) {
		var out = new LinkedHashMap<String, Object>();
		out.put("jobId", job.getId());
		out.put("title", job.getTitle());
		out.put("reference", job.getJobReference());
		out.put("status", job.getStatus());
		out.put("awaitingMatching", Boolean.TRUE.equals(job.getMatchingReportsNeeded()));
		out.put("matchingStaleReason", job.getMatchingStaleReason());
		out.put("newCandidates", job.getNewCandidateCount());
		out.put("matchingTopN", job.getMatchingTopN());
		out.put("lastMatchedAt", job.getLastMatchedAt() != null ? job.getLastMatchedAt().toString() : null);
		out.put("createdAt", job.getCreatedAt() != null ? job.getCreatedAt().toString() : null);
		return out;
	}

	static Map<String, Object> detail(JobPostDTO job) {
		var out = summary(job);
		var text = job.getDescription() != null ? Jsoup.parse(job.getDescription()).text() : null;
		out.put("description", ToolArgs.truncate(text, 3000));
		return out;
	}
}
