package ai.qorva.core.service.orchestrators.handlers;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dto.CandidateCardDTO;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CandidateCardMapper {

	static CandidateCardDTO toCard(CV cv) {
		String id = cv.getId() != null ? cv.getId().toString() : null;

		String name = null;
		String role = null;
		String locationHint = null;
		if (cv.getPersonalInformation() != null) {
			name = cv.getPersonalInformation().getName();
			role = cv.getPersonalInformation().getRole();
		}

		String seniorityLevel = null;
		if (cv.getCandidateClustering() != null) {
			seniorityLevel = cv.getCandidateClustering().getSeniorityLevel();
		}

		List<String> topSkills = List.of();
		if (cv.getKeySkills() != null) {
			topSkills = cv.getKeySkills().stream()
				.filter(ks -> ks.getSkills() != null)
				.flatMap(ks -> ks.getSkills().stream())
				.limit(5)
				.collect(Collectors.toList());
		}

		return new CandidateCardDTO(
			id,
			name != null ? name : "Unknown",
			role != null ? role : "Unknown",
			topSkills,
			seniorityLevel != null ? seniorityLevel : "Unknown",
			cv.getScore(),
			locationHint
		);
	}

	private CandidateCardMapper() {}
}
