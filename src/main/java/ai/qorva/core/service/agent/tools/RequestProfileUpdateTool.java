package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.CandidateEmailTemplateService;
import ai.qorva.core.service.CandidateUpdateEmailService;
import ai.qorva.core.service.CandidateUpdateRequestSender;
import ai.qorva.core.service.TenantService;
import ai.qorva.core.service.UserService;
import ai.qorva.core.service.agent.AgentApproval;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Asks candidates to update their own profile: Data Health's profile-update request (a private link to a form, by
 * email), one candidate at a time instead of a whole issue. An approval action — it emails candidates — unless the
 * rule that started the run pre-approved it. The card shows who is asked and who is skipped (no email, unsubscribed,
 * a request in progress, or one answered or expired recently); addresses come from the profiles, never the model.
 */
@Slf4j
@Component
public class RequestProfileUpdateTool implements AgentTool {

	static final int MAX_CANDIDATES = 25;

	private final CVService cvService;
	private final CandidateUpdateRequestSender sender;
	private final CandidateEmailTemplateService templateService;
	private final TenantService tenantService;
	private final UserService userService;

	public RequestProfileUpdateTool(CVService cvService, CandidateUpdateRequestSender sender, CandidateEmailTemplateService templateService,
	                                TenantService tenantService, UserService userService) {
		this.cvService = cvService;
		this.sender = sender;
		this.templateService = templateService;
		this.tenantService = tenantService;
		this.userService = userService;
	}

	@Override
	public String name() {
		return "request_profile_update";
	}

	@Override
	public String description() {
		return "Ask up to 25 candidates to update their own profile: each gets an email with a private link to a form where "
			+ "they confirm or correct their details and may upload a newer CV (Data Health's profile-update request). Use it "
			+ "for outdated or incomplete profiles, instead of drafting an email. Candidates with no email, who unsubscribed, "
			+ "or who were asked recently are skipped. templateId (optional) is one of the company's email templates. "
			+ "Needs the recruiter's approval.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "cvIds":{"type":"array","items":{"type":"string"},"minItems":1,"maxItems":25},
			  "templateId":{"type":"string","description":"An email template id; the standard message when omitted"}
			},"required":["cvIds"],"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.APPROVAL;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.MODIFY_CV);
	}

	@Override
	public boolean outbound() {
		return true;
	}

	@Override
	public AgentToolResult preview(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var ids = ToolArgs.list(args, "cvIds").stream().distinct().toList();
		if (ids.isEmpty()) return AgentToolResult.error("cvIds is required");
		if (ids.size() > MAX_CANDIDATES) return AgentToolResult.error("At most " + MAX_CANDIDATES + " candidates at a time");
		var templateId = ToolArgs.text(args, "templateId");
		String templateName = null;
		if (templateId != null) {
			try {
				templateName = templateService.findOwned(ctx.tenantId(), templateId).getName();
			} catch (QorvaException e) {
				return AgentToolResult.error("Unknown email template: " + templateId);
			}
		}
		var rows = new ArrayList<Map<String, Object>>();
		var links = new ArrayList<AgentRun.Link>();
		int toSend = 0;
		for (var id : ids) {
			CVDTO cv;
			try {
				cv = cvService.findOneById(id);
			} catch (QorvaException e) {
				return AgentToolResult.error("Unknown candidate: " + id);
			}
			var outcome = sender.check(ctx.tenantId(), cv);
			if (outcome == CandidateUpdateRequestSender.Outcome.SENT) toSend++;
			var name = CvProjections.name(cv);
			var row = new LinkedHashMap<String, Object>();
			row.put("cvId", cv.getId());
			row.put("name", name != null ? name : "");
			row.put("email", mask(email(cv)));
			row.put("outcome", outcome.name());
			rows.add(row);
			links.add(new AgentRun.Link("CV", cv.getId(), name));
		}
		if (toSend == 0) {
			return AgentToolResult.error("None of these candidates can be asked now (no email, unsubscribed, or asked recently). "
				+ "Tell the recruiter; do not retry.");
		}
		var card = new LinkedHashMap<String, Object>();
		card.put("candidates", rows);
		card.put("toSend", toSend);
		card.put("templateName", templateName);
		return AgentToolResult.ok(card, "agent.step.request_profile_update", Map.of("count", String.valueOf(toSend)), links);
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) {
		return AgentToolResult.error("Profile-update requests need the recruiter's approval.");
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx, AgentApproval approval) throws QorvaException {
		var templateId = ToolArgs.text(args, "templateId");
		CandidateUpdateEmailService.CustomTemplate template = null;
		if (templateId != null) {
			var stored = templateService.findOwned(ctx.tenantId(), templateId);
			template = new CandidateUpdateEmailService.CustomTemplate(stored.getSubject(), stored.getBodyText());
		}
		var tenantName = tenantService.findOneById(ctx.tenantId()).getTenantName();
		var senderName = senderName(ctx.userEmail());
		var sent = new ArrayList<String>();
		var skipped = new ArrayList<Map<String, Object>>();
		var links = new ArrayList<AgentRun.Link>();
		for (var id : ToolArgs.list(args, "cvIds").stream().distinct().limit(MAX_CANDIDATES).toList()) {
			var cv = cvService.findOneById(id);
			var name = CvProjections.name(cv);
			CandidateUpdateRequestSender.Outcome outcome;
			try {
				outcome = sender.send(ctx.tenantId(), cv, tenantName, ctx.language(), template, senderName);
			} catch (QorvaException | RuntimeException e) {
				log.warn("Profile-update request for CV {} failed: {}", id, e.getMessage());
				skipped.add(Map.of("name", name != null ? name : "", "reason", "SEND_FAILED"));
				continue;
			}
			if (outcome == CandidateUpdateRequestSender.Outcome.SENT) {
				sent.add(name != null ? name : id);
				links.add(new AgentRun.Link("CV", cv.getId(), name));
			} else {
				skipped.add(Map.of("name", name != null ? name : "", "reason", outcome.name()));
			}
		}
		var data = new LinkedHashMap<String, Object>();
		data.put("sent", sent);
		data.put("skipped", skipped);
		return AgentToolResult.ok(data, "agent.step.request_profile_update_done",
			Map.of("count", String.valueOf(sent.size()), "skipped", String.valueOf(skipped.size())), links);
	}

	private String senderName(String userEmail) {
		var user = userService.findByEmail(userEmail);
		if (user == null) return null;
		var name = ((user.getFirstName() != null ? user.getFirstName() : "") + " " + (user.getLastName() != null ? user.getLastName() : "")).trim();
		return name.isEmpty() ? null : name;
	}

	private static String email(CVDTO cv) {
		var info = cv.getPersonalInformation();
		return info != null && info.getContact() != null ? info.getContact().getEmail() : null;
	}

	/** {@code a***@example.com}: enough for the recruiter to recognise it, not to copy it from a screenshot. */
	static String mask(String email) {
		if (!StringUtils.hasText(email) || !email.contains("@")) return null;
		var at = email.indexOf('@');
		return email.charAt(0) + "***" + email.substring(at);
	}
}
