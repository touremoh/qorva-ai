package ai.qorva.core.service.ats.connectors;

import ai.qorva.core.service.ats.AtsModels.MatchWriteBack;

/** The one note body every connector writes back — plain text, ATS-agnostic. */
final class NoteFormat {

	private NoteFormat() {}

	static String text(MatchWriteBack payload) {
		var sb = new StringBuilder("Qorva match score");
		if (payload.jobTitle() != null) {
			sb.append(" — ").append(payload.jobTitle());
		}
		sb.append(": ");
		sb.append(payload.score() != null ? Math.round(payload.score()) + "/100" : "n/a");
		if (payload.headline() != null && !payload.headline().isBlank()) {
			sb.append('\n').append(payload.headline());
		}
		if (payload.reportUrl() != null) {
			sb.append('\n').append("Full report: ").append(payload.reportUrl());
		}
		return sb.toString();
	}
}
