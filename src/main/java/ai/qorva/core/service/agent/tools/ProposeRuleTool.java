package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dto.AgentData;
import ai.qorva.core.enums.UserActionsEnum;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.agent.AgentApproval;
import ai.qorva.core.service.agent.AgentRiskTier;
import ai.qorva.core.service.agent.AgentTool;
import ai.qorva.core.service.agent.AgentToolContext;
import ai.qorva.core.service.agent.AgentToolResult;
import ai.qorva.core.service.agent.rules.AgentRuleService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns "whenever …" / "every Monday …" in chat into a standing rule — shown as a card first, created when the
 * recruiter approves. Never offered to a run that a rule started.
 */
@Component
public class ProposeRuleTool implements AgentTool {

	private final AgentRuleService ruleService;

	public ProposeRuleTool(AgentRuleService ruleService) {
		this.ruleService = ruleService;
	}

	@Override
	public String name() {
		return "propose_rule";
	}

	@Override
	public String description() {
		return "Propose a standing rule when the recruiter asks for something to happen automatically from now on "
			+ "(\"whenever…\", \"each time…\", \"every Monday…\"). The rule starts a Copilot task, as the recruiter, each "
			+ "time its trigger fires: CV_ADDED (new candidates; source ANY, ATS or MANUAL), CV_SCORED (candidates scored on "
			+ "a job — jobId from list_jobs, or any job — with minScore 0-100 and/or recommendedOnly = recommended for an "
			+ "interview), SCHEDULE (DAILY or WEEKLY at hour 0-23, weekday 1=Monday..7), ATS_SYNC_FINISHED (an ATS import "
			+ "finished; connectionId from list_ats_connections, or any), JOB_NEEDS_MATCHING (an open job's matching results "
			+ "became out of date — jobId or any job; staleReasons any of NEVER_RUN = new job, JOB_CHANGED, NEW_CANDIDATES, "
			+ "CANDIDATE_CHANGED, all when omitted), REPORT_STATUS_CHANGED (a recruiter moved a candidate on a job — jobId or any "
			+ "job; toStatuses any of NEW, CONTACTED, SHORTLISTED, INTERVIEWING, OFFERED, HIRED, REJECTED, WITHDRAWN, any when "
			+ "omitted). The goal is what the task must do; it may use {{candidates}}, {{job}}, "
			+ "{{count}} and {{sync}}, filled in when it fires — e.g. \"Run matching for {{job}} with the top 5 candidates\". "
			+ "Matching normally waits for the recruiter's approval; set autoApproveMatching (with autoApproveMaxActions, the "
			+ "most one matching may cost) only when the recruiter asks for it to run without asking. The recruiter approves "
			+ "the rule before it exists; it never acts on records that existed before.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "name":{"type":"string","description":"Short name, e.g. Invite strong Java matches"},
			  "goal":{"type":"string","description":"What each task does, e.g. Draft an interview invitation for {{candidates}} for {{job}}."},
			  "trigger":{"type":"object","properties":{
			    "type":{"type":"string","enum":["CV_ADDED","CV_SCORED","SCHEDULE","ATS_SYNC_FINISHED","JOB_NEEDS_MATCHING","REPORT_STATUS_CHANGED"]},
			    "source":{"type":"string","enum":["ANY","ATS","MANUAL"]},
			    "jobId":{"type":"string"},
			    "minScore":{"type":"integer","minimum":0,"maximum":100},
			    "recommendedOnly":{"type":"boolean"},
			    "frequency":{"type":"string","enum":["DAILY","WEEKLY"]},
			    "hour":{"type":"integer","minimum":0,"maximum":23},
			    "weekday":{"type":"integer","minimum":1,"maximum":7},
			    "connectionId":{"type":"string"},
			    "staleReasons":{"type":"array","items":{"type":"string","enum":["NEVER_RUN","JOB_CHANGED","NEW_CANDIDATES","CANDIDATE_CHANGED"]}},
			    "toStatuses":{"type":"array","items":{"type":"string","enum":["NEW","CONTACTED","SHORTLISTED","INTERVIEWING","OFFERED","HIRED","REJECTED","WITHDRAWN"]}}},
			   "required":["type"],"additionalProperties":false},
			  "dailyRunCap":{"type":"integer","minimum":1,"description":"Max tasks per day (default 20)"},
			  "autoApproveMatching":{"type":"boolean","description":"Run matching without asking the recruiter"},
			  "autoApproveMaxActions":{"type":"integer","minimum":1,"maximum":500,"description":"Most matching actions one matching may cost without asking (default 50)"}},
			 "required":["name","goal","trigger"],"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.APPROVAL;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.USE_AGENT);
	}

	@Override
	public boolean available(AgentToolContext ctx) {
		return AgentRun.ORIGIN_CHAT.equals(ctx.origin()) && ruleService.rulesEnabled();
	}

	@Override
	public AgentToolResult preview(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var request = request(args, ctx);
		AgentData.TriggerView trigger;
		try {
			trigger = AgentRuleService.triggerView(ruleService.validate(ctx.tenantId(), ctx.userEmail(), ctx.language(), request).getTrigger());
		} catch (QorvaException e) {
			return AgentToolResult.error("This rule is not valid: check the trigger (type, job, connection, hour), the name, "
				+ "the goal and the daily cap.");
		}
		var card = new LinkedHashMap<String, Object>();
		card.put("name", request.getName().strip());
		card.put("trigger", trigger);
		card.put("goalTemplate", request.getGoalTemplate().strip());
		card.put("dailyRunCap", request.getDailyRunCap());
		if (Boolean.TRUE.equals(request.getAutoApproveMatching())) {
			card.put("autoApproveMatching", true);
			card.put("autoApproveMaxActions", request.getAutoApproveMaxActions());
		}
		return AgentToolResult.ok(card, "agent.step.propose_rule", Map.of("name", request.getName().strip()), List.of());
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) {
		return AgentToolResult.error("Creating a rule needs the recruiter's approval.");
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx, AgentApproval approval) throws QorvaException {
		var rule = ruleService.create(ctx.tenantId(), ctx.userEmail(), ctx.language(), request(args, ctx));
		var data = new LinkedHashMap<String, Object>();
		data.put("created", true);
		data.put("ruleId", rule.id());
		data.put("note", "The rule is active. It only acts on records that arrive from now on; the recruiter can pause or "
			+ "edit it in Copilot → Rules.");
		return AgentToolResult.ok(data, "agent.step.rule_created", Map.of("name", rule.name()), List.of());
	}

	private static AgentData.RuleRequest request(JsonNode args, AgentToolContext ctx) {
		var request = new AgentData.RuleRequest();
		request.setName(ToolArgs.text(args, "name"));
		request.setGoalTemplate(ToolArgs.text(args, "goal"));
		request.setDailyRunCap(ToolArgs.optionalInteger(args, "dailyRunCap"));
		request.setAutoApproveMatching(args.path("autoApproveMatching").asBoolean(false) ? Boolean.TRUE : null);
		request.setAutoApproveMaxActions(ToolArgs.optionalInteger(args, "autoApproveMaxActions"));
		var t = args.path("trigger");
		var trigger = new AgentData.TriggerRequest();
		trigger.setType(ToolArgs.text(t, "type"));
		trigger.setSource(ToolArgs.text(t, "source"));
		trigger.setJobPostId(ToolArgs.text(t, "jobId"));
		trigger.setMinScore(ToolArgs.optionalInteger(t, "minScore"));
		trigger.setRecommendedOnly(t.path("recommendedOnly").asBoolean(false) ? Boolean.TRUE : null);
		trigger.setFrequency(ToolArgs.text(t, "frequency"));
		trigger.setHour(ToolArgs.optionalInteger(t, "hour"));
		trigger.setWeekday(ToolArgs.optionalInteger(t, "weekday"));
		// The schedule runs in the recruiter's own time zone, as their browser reported it.
		trigger.setZoneId(ctx.timeZone());
		trigger.setConnectionId(ToolArgs.text(t, "connectionId"));
		var reasons = ToolArgs.list(t, "staleReasons");
		trigger.setStaleReasons(reasons.isEmpty() ? null : reasons);
		var statuses = ToolArgs.list(t, "toStatuses");
		trigger.setToStatuses(statuses.isEmpty() ? null : statuses);
		request.setTrigger(trigger);
		return request;
	}
}
