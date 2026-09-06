package ai.qorva.core.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Link between a Qorva document (CV or job post) and its source record in an external
 * ATS. The (provider, externalId) pair is the idempotency key for sync upserts; the
 * resume content hash lets re-syncs skip unchanged files without an LLM call.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtsRef implements Serializable {

	private String provider;
	private String connectionId;

	/** Candidate id (CVs) or job id (job posts) in the external ATS. */
	private String externalId;

	/** Application/opportunity id where the provider separates candidates from applications. */
	private String externalApplicationId;

	/** Deep link to the record in the ATS UI, when the provider exposes one. */
	private String externalUrl;

	/** SHA-256 of the last imported resume file (hex). */
	private String resumeContentHash;

	private Instant lastImportedAt;

	/** Identity of the linked record: the same pair the sync upserts on. */
	private String key() {
		return provider + "|" + externalId;
	}

	/**
	 * Union of two ref lists keyed by (provider, externalId), most recently imported entry
	 * winning a collision. Used when two CVs are merged: a candidate can be linked to
	 * several ATSs at once, and dropping a link makes the next sync of that provider
	 * re-import and re-extract the same person.
	 */
	public static List<AtsRef> merge(List<AtsRef> primary, List<AtsRef> secondary) {
		var byKey = new LinkedHashMap<String, AtsRef>();
		for (var list : List.of(primary != null ? primary : List.<AtsRef>of(),
			secondary != null ? secondary : List.<AtsRef>of())) {
			for (var ref : list) {
				if (ref == null || ref.getProvider() == null || ref.getExternalId() == null) {
					continue;
				}
				byKey.merge(ref.key(), ref, (kept, candidate) -> newer(kept, candidate));
			}
		}
		return byKey.isEmpty() ? null : new ArrayList<>(byKey.values());
	}

	private static AtsRef newer(AtsRef kept, AtsRef candidate) {
		if (kept.getLastImportedAt() == null) {
			return candidate;
		}
		if (candidate.getLastImportedAt() == null) {
			return kept;
		}
		return candidate.getLastImportedAt().isAfter(kept.getLastImportedAt()) ? candidate : kept;
	}
}
