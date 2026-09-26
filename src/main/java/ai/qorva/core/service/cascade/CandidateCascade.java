package ai.qorva.core.service.cascade;

import ai.qorva.core.dao.repository.CandidateOutreachRepository;
import ai.qorva.core.dao.repository.CandidateUpdateRequestRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-candidate records: outreach history and self-service update requests, both keyed by CV. */
@Component
public class CandidateCascade implements CascadeParticipant {

	private final CandidateOutreachRepository outreach;
	private final CandidateUpdateRequestRepository updateRequests;

	public CandidateCascade(CandidateOutreachRepository outreach, CandidateUpdateRequestRepository updateRequests) {
		this.outreach = outreach;
		this.updateRequests = updateRequests;
	}

	@Override
	public List<Deleted> onParentsDeleted(CascadeResource parent, String tenantId, Collection<String> parentIds) {
		if (parent != CascadeResource.CV) {
			return List.of();
		}
		return List.of(
			Deleted.of("candidate_outreach", outreach.deleteByTenantIdAndCvIdIn(tenantId, parentIds)),
			Deleted.of("candidate_update_requests", updateRequests.deleteByTenantIdAndCvIdIn(tenantId, parentIds)));
	}

	@Override
	public Map<String, Long> onTenantPurge(String tenantId, PurgeScope scope) {
		var counts = new LinkedHashMap<String, Long>();
		counts.put("candidate_outreach", outreach.deleteByTenantId(tenantId));
		counts.put("candidate_update_requests", updateRequests.deleteByTenantId(tenantId));
		return counts;
	}
}
