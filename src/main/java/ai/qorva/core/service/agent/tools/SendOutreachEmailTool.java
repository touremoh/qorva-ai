package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.agent.AgentApproval;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ai.qorva.core.dto.CandidateOutreachData;
import ai.qorva.core.service.CandidateOutreachService;
import ai.qorva.core.service.mailbox.MailboxConnectionService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sends an email to a candidate from the recruiter's connected mailbox — only through an approval card that shows
 * the exact message. The address always comes from the candidate's profile; the model never supplies it.
 */
@Component
public class SendOutreachEmailTool implements AgentTool {

	static final int SUBJECT_MAX = 200;
	static final int BODY_MAX = 8000;

	private final CandidateOutreachService outreachService;
	private final MailboxConnectionService mailboxService;

	public SendOutreachEmailTool(CandidateOutreachService outreachService, MailboxConnectionService mailboxService) {
		this.outreachService = outreachService;
		this.mailboxService = mailboxService;
	}

	@Override
	public String name() {
		return "send_outreach_email";
	}

	@Override
	public String description() {
		return "Send an email to a candidate from the recruiter's own mailbox. Write the subject and body yourself "
			+ "(draft_outreach can help). The recruiter sees the exact message on an approval card and decides; nothing "
			+ "is sent without their approval. The address is taken from the candidate's profile.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "cvId":{"type":"string"},
			  "jobId":{"type":"string","description":"The job the email is about, if any"},
			  "subject":{"type":"string","maxLength":200},
			  "body":{"type":"string","maxLength":8000,"description":"Plain text"}
			},"required":["cvId","subject","body"],"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.APPROVAL;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.CONTACT_CANDIDATE);
	}

	@Override
	public boolean outbound() {
		return true;
	}

	@Override
	public boolean available(AgentToolContext ctx) {
		return mailboxService.composerState(ctx.tenantId(), ctx.userEmail()).state() == CandidateOutreachData.MailboxState.MICROSOFT;
	}

	@Override
	public AgentToolResult preview(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var cvId = ToolArgs.text(args, "cvId");
		var subject = ToolArgs.text(args, "subject");
		var body = ToolArgs.text(args, "body");
		if (cvId == null || subject == null || body == null) return AgentToolResult.error("cvId, subject and body are required");
		var invalid = invalidMessage(subject, body);
		if (invalid != null) return AgentToolResult.error(invalid);

		var context = outreachService.context(ctx.tenantId(), ctx.userEmail(), cvId);
		if (context.mailbox() != CandidateOutreachData.MailboxState.MICROSOFT) {
			return AgentToolResult.error("The recruiter's mailbox is not connected (Settings → Integrations); nothing can be sent.");
		}
		if (context.suppressed()) return AgentToolResult.error("This candidate asked not to be contacted.");
		if (context.email() == null || context.email().isBlank()) return AgentToolResult.error("This candidate has no email address.");

		var card = new LinkedHashMap<String, Object>();
		card.put("cvId", cvId);
		card.put("candidateName", context.candidateName());
		card.put("to", context.email());
		card.put("from", context.mailboxAddress());
		card.put("jobId", ToolArgs.text(args, "jobId"));
		card.put("subject", subject);
		card.put("body", body);
		card.put("lastContactedAt", context.history() != null && !context.history().isEmpty() && context.history().getFirst().getCreatedAt() != null
			? context.history().getFirst().getCreatedAt().toString() : null);
		return AgentToolResult.ok(card, "agent.step.send_outreach_email", Map.of("name", nameOf(context)),
			List.of(new AgentRun.Link("CV", cvId, context.candidateName())));
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) {
		return AgentToolResult.error("Sending an email needs the recruiter's approval.");
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx, AgentApproval approval) throws QorvaException {
		var cvId = ToolArgs.text(args, "cvId");
		var subject = approval.subject() != null ? approval.subject().strip() : ToolArgs.text(args, "subject");
		var body = approval.body() != null ? approval.body().strip() : ToolArgs.text(args, "body");
		var invalid = invalidMessage(subject, body);
		if (invalid != null) return AgentToolResult.error(invalid);

		// The address is resolved again at send time: whatever changed since the card, the profile decides.
		var context = outreachService.context(ctx.tenantId(), ctx.userEmail(), cvId);
		var request = new CandidateOutreachData.SendRequest();
		request.setCvId(cvId);
		request.setJobPostId(ToolArgs.text(args, "jobId"));
		request.setTo(context.email());
		request.setSubject(subject);
		request.setBody(body);
		outreachService.send(ctx.tenantId(), ctx.userEmail(), request, ctx.runId());

		var data = new LinkedHashMap<String, Object>();
		data.put("sent", true);
		data.put("to", context.email());
		data.put("subject", subject);
		data.put("editedByRecruiter", approval.subject() != null || approval.body() != null);
		return AgentToolResult.ok(data, "agent.step.email_sent", Map.of("name", nameOf(context)),
			List.of(new AgentRun.Link("CV", cvId, context.candidateName())));
	}

	static String invalidMessage(String subject, String body) {
		if (subject == null || subject.isBlank() || body == null || body.isBlank()) return "Subject and body must not be empty";
		if (subject.length() > SUBJECT_MAX) return "The subject is at most " + SUBJECT_MAX + " characters";
		if (body.length() > BODY_MAX) return "The body is at most " + BODY_MAX + " characters";
		return null;
	}

	private static String nameOf(CandidateOutreachData.ContextResponse context) {
		return context.candidateName() != null ? context.candidateName() : "";
	}
}
