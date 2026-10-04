package ai.qorva.core.service;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.utils.QorvaUtils;
import org.springframework.beans.BeanUtils;

/**
 * The part of a CV that a matching report depends on: everything except recruiter annotations
 * (tags), import links, the attachment, audit fields and the values derived from the content
 * (content date, quality flags, contact keys).
 * <p>
 * One definition serves both sides of matching, so they can never disagree: it is what the report
 * prompt receives, and a CV update re-flags open jobs only when this view changed.
 * <p>
 * A denylist on purpose: a field added to {@link CVDTO} later counts as matching-relevant until
 * it is listed here, so the failure mode is an extra re-screen, never a stale score.
 */
final class CvMatchingView {

	private CvMatchingView() {
	}

	static CVDTO of(CVDTO cv) {
		var view = new CVDTO();
		BeanUtils.copyProperties(cv, view);
		view.setTags(null);
		view.setAtsRefs(null);
		view.setAttachment(null);
		// Derived from the content (recomputed on every update; the content date is capped at "now"),
		// so comparing them adds nothing and would re-flag on the clock alone.
		view.setContentDate(null);
		view.setContentDateSource(null);
		view.setQualityFlags(null);
		view.setContactKeys(null);
		view.setMatchCheckPending(null);
		view.setMatchCheckPendingSince(null);
		view.setCreatedAt(null);
		view.setCreatedBy(null);
		view.setLastUpdatedAt(null);
		view.setLastUpdatedBy(null);
		return view;
	}

	/** Stable serialisation of {@link #of(CVDTO)}; equal fingerprints mean the same matching input. */
	static String fingerprint(CVDTO cv) {
		return QorvaUtils.toJSON(of(cv));
	}
}
