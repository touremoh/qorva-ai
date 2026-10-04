package ai.qorva.core.service;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.utils.QorvaUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes of what a matching report is computed from. Two reports with the same {@link #input} hash were
 * generated from exactly the same prompt, so the stored one is reused instead of paying for a new one.
 * <p>
 * Each part is the very value the report prompt receives — {@link CvMatchingView} for the candidate, the
 * job's title + description and scoring rules, the language, and the model/prompt version — never a
 * hand-picked field list, so an input added to the prompt later cannot be missed.
 */
final class MatchingFingerprint {

	private MatchingFingerprint() {
	}

	/** The candidate's side alone — compared on its own to tell when an edit made a report stale. */
	static String cv(CVDTO cv) {
		return sha256(CvMatchingView.fingerprint(cv));
	}

	static String job(JobPostDTO job) {
		var rules = job.getScoringRules() != null ? QorvaUtils.toJSON(job.getScoringRules()) : "";
		return sha256(job.toJobTitleAndDescription() + "\n" + rules);
	}

	static String input(String cvFingerprint, String jobFingerprint, String languageCode, String reportVersion) {
		return sha256(String.join("\n", cvFingerprint, jobFingerprint, languageCode, reportVersion));
	}

	static String sha256(String value) {
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
