package ai.qorva.core.dto;

import ai.qorva.core.dao.entity.CandidateEmailTemplate;

import java.time.Instant;
import java.util.List;

/** API shapes for recruiter-authored candidate-update invitation templates. */
public final class CandidateEmailTemplateData {

	private CandidateEmailTemplateData() {}

	public record SaveRequest(String name, String subject, String bodyText) {}

	public record TemplateView(
		String id,
		String name,
		String subject,
		String bodyText,
		String createdBy,
		Instant createdAt,
		Instant updatedAt
	) {
		public static TemplateView from(CandidateEmailTemplate template) {
			return new TemplateView(template.getId(), template.getName(), template.getSubject(),
				template.getBodyText(), template.getCreatedBy(), template.getCreatedAt(), template.getUpdatedAt());
		}
	}

	/** {@code limit} is the plan cap on saved templates; null → unlimited. */
	public record TemplateList(List<TemplateView> templates, Integer limit) {}

	/** Preview accepts unsaved drafts — the editor live-previews before saving. */
	public record PreviewRequest(String subject, String bodyText, String language) {}

	public record PreviewResponse(String subject, String html) {}
}
