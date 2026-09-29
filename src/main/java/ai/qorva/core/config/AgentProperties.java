package ai.qorva.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Copilot (AI agent) switches and budgets. Every run is bounded by these limits, whatever the
 * model decides; hitting one ends the run with what it has so far.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qorva.ai.agent")
public class AgentProperties {

	/** Kill switch: off → the worker claims nothing, starting a run answers 503, the app hides Copilot. */
	private boolean enabled = false;

	/** Model that plans and calls tools. Must support tool calling. */
	private String model = "gpt-5.6-terra";

	/** Model turns per run. */
	private int maxSteps = 20;

	/** Tool calls per run. */
	private int maxToolCalls = 40;

	/** Prompt + completion tokens per run. */
	private int maxTokens = 200_000;

	/** Time the run may spend executing (paused time excluded). */
	private int maxRunningSeconds = 600;

	/** Max characters of one tool result sent back to the model. */
	private int toolResultMaxChars = 6000;

	/** Runs executed at the same time on one instance. */
	private int workerConcurrency = 4;

	/** Backstop poll of the worker; a new run also wakes it immediately. */
	private long pollDelayMs = 5000;

	/** Max length of a goal typed by the user. */
	private int maxGoalLength = 2000;
}
