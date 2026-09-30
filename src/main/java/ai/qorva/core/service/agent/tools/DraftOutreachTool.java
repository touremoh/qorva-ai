package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import ai.qorva.core.dto.CandidateOutreachData;
import ai.qorva.core.enums.OutreachIntentEnum;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.CandidateOutreachDraftService;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drafts an email to a candidate with the outreach composer's AI. Never sends and never stores: the
 * recruiter reads the draft in the answer and sends it from the composer (sending is an approval
 * action, not available to the agent yet).
 */
@Component
public class DraftOutreachTool implements AgentTool {

	private final CandidateOutreachDraftService draftService;
	private final CVService cvService;

	public DraftOutreachTool(CandidateOutreachDraftService draftService, CVService cvService) {
		this.draftService = draftService;
		this.cvService = cvService;
	}

	@Override
	public String name() {
		return "draft_outreach";
	}

	@Override
	public String description() {
		return "Draft an email to a candidate (intro, interview invitation, follow-up, keep warm, or custom). "
			+ "Returns the subject and body to show the recruiter. It does not send anything.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "cvId":{"type":"string"},
			  "jobId":{"type":"string","description":"The job the email is about, if any"},
			  "intent":{"type":"string","enum":["INTRO","INTERVIEW","FOLLOW_UP","KEEP_WARM","CUSTOM"]},
			  "instructions":{"type":"string","maxLength":1000,"description":"What the email must say, in the recruiter's words"}
			},"required":["cvId","intent"],"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.WRITE_INTERNAL;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.CONTACT_CANDIDATE);
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var cvId = ToolArgs.text(args, "cvId");
		var intent = OutreachIntentEnum.fromValue(ToolArgs.text(args, "intent"));
		if (cvId == null || intent == null) {
			return AgentToolResult.error("cvId and intent (" + Arrays.toString(OutreachIntentEnum.values()) + ") are required");
		}
		var instructions = ToolArgs.text(args, "instructions");
		if (instructions != null && instructions.length() > 1000) return AgentToolResult.error("instructions are at most 1000 characters");
		var cv = cvService.findOneById(cvId);

		var request = new CandidateOutreachData.DraftRequest();
		request.setCvId(cv.getId());
		request.setJobPostId(ToolArgs.text(args, "jobId"));
		request.setIntent(intent.name());
		request.setInstructions(instructions);
		var draft = draftService.draft(ctx.tenantId(), ctx.userEmail(), request, ctx.language());

		var data = new LinkedHashMap<String, Object>();
		data.put("cvId", cv.getId());
		data.put("subject", draft.subject());
		data.put("body", draft.body());
		data.put("sent", false);
		var name = CvProjections.name(cv);
		return AgentToolResult.ok(data, "agent.step.draft_outreach", Map.of("name", name != null ? name : ""),
			List.of(new AgentRun.Link("CV", cv.getId(), name)));
	}
}
