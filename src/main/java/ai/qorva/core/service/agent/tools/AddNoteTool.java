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
import ai.qorva.core.dto.NoteRequest;
import ai.qorva.core.enums.NoteTargetTypeEnum;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.NoteService;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** A note on a candidate, written as the user (the author shown in the notes panel). */
@Component
public class AddNoteTool implements AgentTool {

	private final NoteService noteService;
	private final CVService cvService;

	public AddNoteTool(NoteService noteService, CVService cvService) {
		this.noteService = noteService;
		this.cvService = cvService;
	}

	@Override
	public String name() {
		return "add_note";
	}

	@Override
	public String description() {
		return "Add a note to a candidate's profile, visible to the team. Write it in the recruiter's language, "
			+ "factual and short. Only when the recruiter asked for notes.";
	}

	@Override
	public String inputSchema() {
		return """
			{"type":"object","properties":{
			  "cvId":{"type":"string"},
			  "text":{"type":"string","maxLength":4000}
			},"required":["cvId","text"],"additionalProperties":false}""";
	}

	@Override
	public AgentRiskTier tier() {
		return AgentRiskTier.WRITE_INTERNAL;
	}

	@Override
	public Set<UserActionsEnum> requiredActions() {
		return Set.of(UserActionsEnum.MODIFY_CV);
	}

	@Override
	public AgentToolResult execute(JsonNode args, AgentToolContext ctx) throws QorvaException {
		var cvId = ToolArgs.text(args, "cvId");
		var text = ToolArgs.text(args, "text");
		if (cvId == null || text == null) return AgentToolResult.error("cvId and text are required");
		var cv = cvService.findOneById(cvId);
		var note = noteService.create(ctx.tenantId(), ctx.userEmail(), new NoteRequest(NoteTargetTypeEnum.CV.name(), cv.getId(), text));
		var name = CvProjections.name(cv);
		return AgentToolResult.ok(Map.of("noteId", note.getId(), "cvId", cv.getId()), "agent.step.add_note",
			Map.of("name", name != null ? name : ""), List.of(new AgentRun.Link("CV", cv.getId(), name)));
	}
}
