package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dto.MentionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MentionResolver {

    private final CVRepository cvRepository;
    private final JobPostRepository jobPostRepository;

    public record ResolvedMentions(List<CV> candidates, List<JobPost> jobs) {
        public boolean isEmpty() {
            return candidates.isEmpty() && jobs.isEmpty();
        }
    }

    public ResolvedMentions resolve(Collection<MentionDTO> mentions, String tenantId) {
        if (mentions == null || mentions.isEmpty() || tenantId == null || tenantId.isBlank()) {
            return new ResolvedMentions(List.of(), List.of());
        }

        List<String> candidateIds = new ArrayList<>();
        List<String> jobIds = new ArrayList<>();
        for (MentionDTO m : mentions) {
            if (m == null || m.id() == null || m.type() == null) continue;
            if (MentionDTO.TYPE_CANDIDATE.equalsIgnoreCase(m.type())) {
                candidateIds.add(m.id());
            } else if (MentionDTO.TYPE_JOB.equalsIgnoreCase(m.type())) {
                jobIds.add(m.id());
            }
        }

        List<CV> candidates = candidateIds.isEmpty()
                ? List.of()
                : safeFindCVs(candidateIds, tenantId);
        List<JobPost> jobs = jobIds.isEmpty()
                ? List.of()
                : safeFindJobs(jobIds, tenantId);

        if (candidates.size() != candidateIds.size() || jobs.size() != jobIds.size()) {
            log.debug("Mention resolution dropped {} candidate(s) and {} job(s) (missing or cross-tenant)",
                    candidateIds.size() - candidates.size(),
                    jobIds.size() - jobs.size());
        }

        return new ResolvedMentions(candidates, jobs);
    }

    private List<CV> safeFindCVs(List<String> ids, String tenantId) {
        try {
            return cvRepository.findByIdInAndTenantId(ids, tenantId);
        } catch (Exception e) {
            log.warn("Failed to resolve candidate mentions for tenant {}: {}", tenantId, e.getMessage());
            return List.of();
        }
    }

    private List<JobPost> safeFindJobs(List<String> ids, String tenantId) {
        try {
            return jobPostRepository.findByIdInAndTenantId(ids, tenantId);
        } catch (Exception e) {
            log.warn("Failed to resolve job mentions for tenant {}: {}", tenantId, e.getMessage());
            return List.of();
        }
    }
}
