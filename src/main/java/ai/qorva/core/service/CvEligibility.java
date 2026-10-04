package ai.qorva.core.service;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.common.ScoringRules;

/**
 * Whether a candidate may appear in a job's matching results at all, in Java: the same rules as the
 * post-filter of {@code CVRepositoryImpl.similaritySearch} (archived candidates never; with the job's
 * "open to work" filter, not those who said they are not; with availability statuses, only those).
 * Keep both in step.
 */
final class CvEligibility {

	private CvEligibility() {
	}

	static boolean eligible(CVDTO cv, ScoringRules rules) {
		if (Boolean.TRUE.equals(cv.getArchived())) {
			return false;
		}
		var info = cv.getPersonalInformation();
		var availability = info != null ? info.getAvailability() : null;
		if (rules != null && Boolean.TRUE.equals(rules.getFilterOpenToWork())
			&& availability != null && Boolean.FALSE.equals(availability.getOpenToWork())) {
			return false;
		}
		var statuses = rules != null ? rules.getAvailabilityStatuses() : null;
		if (statuses != null && !statuses.isEmpty()) {
			return availability != null && statuses.contains(availability.getStatus());
		}
		return true;
	}
}
