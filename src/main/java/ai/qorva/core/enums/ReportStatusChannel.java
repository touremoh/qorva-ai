package ai.qorva.core.enums;

/** How a report's status was changed. */
public enum ReportStatusChannel {
	/** A recruiter, in the app. */
	APP,
	/** Automatically, when an email was sent to the candidate from Qorva. */
	EMAIL,
	/** A Copilot run. */
	COPILOT
}
