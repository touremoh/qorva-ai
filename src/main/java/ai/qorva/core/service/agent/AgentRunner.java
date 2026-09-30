package ai.qorva.core.service.agent;

import ai.qorva.core.config.AgentProperties;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.UsageMonitoringService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/**
 * The agent loop for one claimed run: ask the model, execute the tools it calls, feed the results
 * back, until it answers without tools, a budget is reached, the user cancels, or it fails.
 * <p>
 * Every tool step is persisted as EXECUTING before it runs. A step still EXECUTING when a run is
 * resumed was interrupted: it is never replayed, and the model is told its outcome is unknown.
 */
@Slf4j
@Component
public class AgentRunner {

	static final int MAX_CONSECUTIVE_ERRORS = 3;
	private static final String PROMPT_FILE = "prompts/Agent_system_prompt.md";
	private static final String INTERRUPTED = "Interrupted before completion; outcome unknown. Verify with a read tool before repeating.";

	private final AgentRunStore store;
	private final AgentModelClient modelClient;
	private final AgentToolRegistry registry;
	private final AgentExecutionScope scope;
	private final AgentProperties properties;
	private final UsageMonitoringService usageMonitoringService;
	private final ObjectMapper objectMapper;
	private final String systemPromptTemplate;

	public AgentRunner(AgentRunStore store, AgentModelClient modelClient, AgentToolRegistry registry,
	                   AgentExecutionScope scope, AgentProperties properties,
	                   UsageMonitoringService usageMonitoringService, ObjectMapper objectMapper) throws IOException {
		this.store = store;
		this.modelClient = modelClient;
		this.registry = registry;
		this.scope = scope;
		this.properties = properties;
		this.usageMonitoringService = usageMonitoringService;
		this.objectMapper = objectMapper;
		this.systemPromptTemplate = new ClassPathResource(PROMPT_FILE).getContentAsString(StandardCharsets.UTF_8);
	}

	/** Works on a claimed run until it ends or this instance loses its lease. */
	public void run(AgentRun run) {
		try {
			scope.call(run, ctx -> {
				loop(run, ctx);
				return null;
			});
		} catch (AgentPreconditionException e) {
			log.warn("agent-run {} cannot continue: {}", run.getId(), e.getMessage());
			fail(run, e.reason());
		} catch (Exception e) {
			log.error("agent-run {} crashed", run.getId(), e);
			fail(run, QorvaErrorCodes.AGENT_MODEL_FAILED);
		}
	}

	private void loop(AgentRun run, AgentToolContext ctx) {
		long claimStart = System.currentTimeMillis();
		long accountedUntil = claimStart;
		repairInterrupted(run);
		int consecutiveErrors = trailingErrors(run);

		while (true) {
			if (store.isCancelRequested(run)) {
				run.setStatus(AgentRun.STATUS_CANCELLED);
				finish(run);
				return;
			}
			long now = System.currentTimeMillis();
			run.setRunningMillis(run.getRunningMillis() + (now - accountedUntil));
			accountedUntil = now;
			if (budgetReached(run)) {
				stopEarly(run);
				return;
			}

			var tools = registry.allowedFor(ctx);
			ChatResponse response;
			try {
				response = modelClient.call(AgentHistory.toMessages(systemPrompt(run), run.getHistory()), tools);
			} catch (RuntimeException e) {
				log.error("agent-run {} model call failed", run.getId(), e);
				fail(run, QorvaErrorCodes.AGENT_MODEL_FAILED);
				return;
			}
			// Only a run the model actually worked on counts against the plan.
			meterOnce(run);
			recordTokens(run, response);
			run.setStepCount(run.getStepCount() + 1);
			AssistantMessage assistant = response.getResult().getOutput();
			run.getHistory().add(AgentHistory.assistant(assistant));

			if (!assistant.hasToolCalls()) {
				run.setFinalAnswer(assistant.getText());
				run.setStatus(AgentRun.STATUS_COMPLETED);
				finish(run);
				return;
			}

			var results = new ArrayList<AgentRun.ToolResult>();
			for (var call : assistant.getToolCalls()) {
				var step = startStep(run, call);
				if (!store.saveProgress(run)) {
					log.warn("agent-run {} lost its lease before {}", run.getId(), call.name());
					return;
				}
				long t0 = System.currentTimeMillis();
				var result = execute(call, ctx);
				completeStep(step, result);
				log.info("agent-run {} step={} tool={} tier={} state={} ms={}", run.getId(), step.getSeq(), step.getTool(),
					step.getTier(), step.getState(), System.currentTimeMillis() - t0);
				results.add(new AgentRun.ToolResult(call.id(), call.name(), toModelJson(result)));
				consecutiveErrors = result.ok() ? 0 : consecutiveErrors + 1;
			}
			run.getHistory().add(AgentHistory.toolResults(results));

			if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
				fail(run, QorvaErrorCodes.AGENT_TOO_MANY_ERRORS);
				return;
			}
			if (!store.saveProgress(run)) {
				log.warn("agent-run {} lost its lease", run.getId());
				return;
			}
		}
	}

	private AgentRun.Step startStep(AgentRun run, AssistantMessage.ToolCall call) {
		run.setToolCallCount(run.getToolCallCount() + 1);
		var step = new AgentRun.Step();
		step.setSeq(run.getSteps().size() + 1);
		step.setKind(AgentRun.Step.KIND_TOOL_CALL);
		step.setTool(call.name());
		step.setState(AgentRun.Step.STATE_EXECUTING);
		step.setStartedAt(Instant.now());
		run.getSteps().add(step);
		return step;
	}

	private static void completeStep(AgentRun.Step step, AgentToolResult result) {
		step.setState(result.ok() ? AgentRun.Step.STATE_OK : AgentRun.Step.STATE_ERROR);
		step.setSummaryKey(result.summaryKey());
		step.setSummaryParams(result.summaryParams() != null ? new java.util.LinkedHashMap<>(result.summaryParams()) : new java.util.LinkedHashMap<>());
		step.setLinks(result.links() != null ? new ArrayList<>(result.links()) : new ArrayList<>());
		step.setError(result.error());
		step.setFinishedAt(Instant.now());
	}

	AgentToolResult execute(AssistantMessage.ToolCall call, AgentToolContext ctx) {
		var tool = registry.allowed(call.name(), ctx);
		if (tool.isEmpty()) {
			return AgentToolResult.error("Tool not available: " + call.name());
		}
		if (tool.get().tier() == AgentRiskTier.APPROVAL) {
			// The approval pause is not built yet: an approval tool must never run unattended.
			return AgentToolResult.error("This action needs the user's approval, which is not available yet.");
		}
		JsonNode args;
		try {
			args = call.arguments() == null || call.arguments().isBlank()
				? objectMapper.createObjectNode() : objectMapper.readTree(call.arguments());
		} catch (JsonProcessingException e) {
			return AgentToolResult.error("Arguments are not valid JSON");
		}
		try {
			return tool.get().execute(args, ctx);
		} catch (QorvaException e) {
			return AgentToolResult.error(e.getMessage() != null ? e.getMessage() : "The request was refused");
		} catch (RuntimeException e) {
			log.error("agent tool {} failed", call.name(), e);
			return AgentToolResult.error("The tool failed unexpectedly");
		}
	}

	private String toModelJson(AgentToolResult result) {
		String json;
		try {
			json = objectMapper.writeValueAsString(result.data());
		} catch (JsonProcessingException e) {
			json = "{\"error\":\"result could not be serialised\"}";
		}
		int max = properties.getToolResultMaxChars();
		return json.length() <= max ? json : json.substring(0, max) + "…(truncated)";
	}

	/** Steps left EXECUTING by a crashed worker are closed, and the model gets an answer for every call. */
	private void repairInterrupted(AgentRun run) {
		run.getSteps().stream()
			.filter(s -> AgentRun.Step.STATE_EXECUTING.equals(s.getState()))
			.forEach(s -> {
				s.setState(AgentRun.Step.STATE_ERROR);
				s.setSummaryKey("agent.step.interrupted");
				s.setError(INTERRUPTED);
				s.setFinishedAt(Instant.now());
			});
		var history = run.getHistory();
		if (!history.isEmpty()) {
			var last = history.getLast();
			if (AgentHistory.ASSISTANT.equals(last.getRole()) && last.getToolCalls() != null && !last.getToolCalls().isEmpty()) {
				history.add(AgentHistory.toolResults(last.getToolCalls().stream()
					.map(c -> new AgentRun.ToolResult(c.getId(), c.getName(), "{\"error\":\"" + INTERRUPTED + "\"}"))
					.toList()));
			}
		}
	}

	private static int trailingErrors(AgentRun run) {
		int count = 0;
		for (int i = run.getSteps().size() - 1; i >= 0 && AgentRun.Step.STATE_ERROR.equals(run.getSteps().get(i).getState()); i--) {
			count++;
		}
		return count;
	}

	private boolean budgetReached(AgentRun run) {
		return run.getStepCount() >= properties.getMaxSteps()
			|| run.getToolCallCount() >= properties.getMaxToolCalls()
			|| run.getTokens().total() >= properties.getMaxTokens()
			|| run.getRunningMillis() >= properties.getMaxRunningSeconds() * 1000L;
	}

	/** A budget ends the run as completed, with the last thing the model said. */
	private void stopEarly(AgentRun run) {
		run.setStoppedEarly(true);
		run.setFinalAnswer(run.getHistory().reversed().stream()
			.filter(m -> AgentHistory.ASSISTANT.equals(m.getRole()) && m.getText() != null && !m.getText().isBlank())
			.map(AgentRun.HistoryMessage::getText)
			.findFirst().orElse(null));
		run.setStatus(AgentRun.STATUS_COMPLETED);
		finish(run);
	}

	private void meterOnce(AgentRun run) {
		if (!run.isMetered()) {
			usageMonitoringService.incrementUsage(run.getTenantId(), UsageMonitoringService.FeatureKey.AGENT_RUNS, 1);
			run.setMetered(true);
		}
	}

	private static void recordTokens(AgentRun run, ChatResponse response) {
		var metadata = response.getMetadata();
		if (metadata == null) return;
		var usage = metadata.getUsage();
		var tokens = run.getTokens();
		if (usage != null) {
			tokens.setPrompt(tokens.getPrompt() + (usage.getPromptTokens() != null ? usage.getPromptTokens() : 0));
			tokens.setCompletion(tokens.getCompletion() + (usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0));
		}
		if (metadata.getModel() != null && !metadata.getModel().isBlank()) {
			tokens.setModel(metadata.getModel());
		}
	}

	private void fail(AgentRun run, String reason) {
		run.setStatus(AgentRun.STATUS_FAILED);
		run.setFailureReason(reason);
		finish(run);
	}

	private void finish(AgentRun run) {
		if (!store.finish(run)) {
			log.warn("agent-run {} finished without its lease; result not saved", run.getId());
			return;
		}
		log.info("agent-run {} finished status={} steps={} tools={} tokens={}/{}", run.getId(), run.getStatus(),
			run.getStepCount(), run.getToolCallCount(), run.getTokens().getPrompt(), run.getTokens().getCompletion());
	}

	String systemPrompt(AgentRun run) {
		return systemPromptTemplate
			.replace("{{today}}", LocalDate.now(ZoneOffset.UTC).toString())
			.replace("{{language}}", languageName(run.getLanguage()));
	}

	private static final Map<String, String> LANGUAGES = Map.of(
		"en", "English", "fr", "French", "de", "German", "es", "Spanish", "it", "Italian", "nl", "Dutch", "pt", "Portuguese");

	static String languageName(String code) {
		if (code == null) return "English";
		var primary = code.split("[-_,;]")[0].trim().toLowerCase(Locale.ROOT);
		return LANGUAGES.getOrDefault(primary, "English");
	}
}
