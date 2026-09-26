package ai.qorva.core.service.cascade;

import ai.qorva.core.dao.repository.NoteRepository;
import ai.qorva.core.enums.NoteTargetTypeEnum;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Recruiter notes target a CV or a screening report. */
@Component
public class NoteCascade implements CascadeParticipant {

	private final NoteRepository repository;

	public NoteCascade(NoteRepository repository) {
		this.repository = repository;
	}

	@Override
	public List<Deleted> onParentsDeleted(CascadeResource parent, String tenantId, Collection<String> parentIds) {
		var targetType = switch (parent) {
			case CV -> NoteTargetTypeEnum.CV;
			case MATCHING_REPORT -> NoteTargetTypeEnum.MATCHING_REPORT;
			case JOB_POST -> null;
		};
		if (targetType == null) {
			return List.of();
		}
		return List.of(Deleted.of("notes",
			repository.deleteByTenantIdAndTargetTypeAndTargetIdIn(tenantId, targetType.name(), parentIds)));
	}

	@Override
	public Map<String, Long> onTenantPurge(String tenantId, PurgeScope scope) {
		// Every note targets a CV or a report, so both scopes remove them all.
		return Map.of("notes", repository.deleteByTenantId(tenantId));
	}
}
