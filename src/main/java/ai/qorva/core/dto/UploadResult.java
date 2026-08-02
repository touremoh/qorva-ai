package ai.qorva.core.dto;

import java.time.Instant;
import java.util.List;

/**
 * Per-file outcome of a CV upload. Replaces the old successes-only response so the
 * recruiter sees failures, parse warnings, and duplicate collisions immediately —
 * fixing issues at ingest is the only strategy that scales with library size.
 */
public record UploadResult(
	String fileName,
	String status,              // CREATED | DUPLICATE_DETECTED | FAILED
	CVDTO cv,                   // the newly created CV (CREATED / DUPLICATE_DETECTED)
	DuplicateMatch match,       // set when DUPLICATE_DETECTED
	List<String> warnings,      // notable quality flags (MISSING_PHONE, LOW_AI_CONFIDENCE, …)
	String errorCode            // set when FAILED
) {

	public static final String STATUS_CREATED = "CREATED";
	public static final String STATUS_DUPLICATE_DETECTED = "DUPLICATE_DETECTED";
	public static final String STATUS_FAILED = "FAILED";

	public record DuplicateMatch(
		String existingCvId,
		String existingName,
		String matchType,       // EMAIL | PHONE
		Instant existingCreatedAt
	) {}
}
