package ai.qorva.core.service.cascade;

import ai.qorva.core.dao.repository.MatchingReportRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Screening reports belong to a CV and a job post; they are parents of notes and chats themselves. */
@Component
public class MatchingReportCascade implements CascadeParticipant {

	private final MatchingReportRepository repository;

	public MatchingReportCascade(MatchingReportRepository repository) {
		this.repository = repository;
	}

	@Override
	public List<Deleted> onParentsDeleted(CascadeResource parent, String tenantId, Collection<String> parentIds) {
		if (parent != CascadeResource.CV && parent != CascadeResource.JOB_POST) {
			return List.of();
		}
		var reportIds = new ArrayList<String>();
		long count = 0;
		for (var parentId : parentIds) {
			// Report ids first: their notes and chats can only be found while the reports exist.
			if (parent == CascadeResource.CV) {
				repository.findByTenantIdAndCandidateInfoCandidateId(tenantId, parentId)
					.forEach(r -> reportIds.add(r.getId()));
				count += repository.deleteByTenantIdAndCandidateInfoCandidateId(tenantId, parentId);
			} else {
				repository.findByTenantIdAndJobPostId(tenantId, parentId).forEach(r -> reportIds.add(r.getId()));
				count += repository.deleteByTenantIdAndJobPostId(tenantId, parentId);
			}
		}
		return List.of(new Deleted("matching_reports", count, CascadeResource.MATCHING_REPORT, reportIds));
	}

	@Override
	public Map<String, Long> onTenantPurge(String tenantId, PurgeScope scope) {
		return Map.of("matching_reports", repository.deleteByTenantId(tenantId));
	}
}
