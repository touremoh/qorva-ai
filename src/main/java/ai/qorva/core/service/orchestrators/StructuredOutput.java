package ai.qorva.core.service.orchestrators;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;

/**
 * Chat options for a structured (JSON-schema) answer — the block every extraction, report and
 * insight call used to build by hand.
 */
public final class StructuredOutput {

	private StructuredOutput() {
	}

	/**
	 * @param schemaName the response-format name OpenAI reports back (e.g. {@code cv_parser})
	 * @param jsonSchema the JSON schema the answer must follow
	 * @param strict     OpenAI strict mode (every property required, no extras)
	 */
	public static OpenAiChatOptions options(String model, String schemaName, String jsonSchema, boolean strict, double temperature) {
		return OpenAiChatOptions.builder()
			.model(model)
			.responseFormat(ResponseFormat.builder()
				.type(ResponseFormat.Type.JSON_SCHEMA)
				.jsonSchema(ResponseFormat.JsonSchema.builder()
					.name(schemaName)
					.schema(jsonSchema)
					.strict(strict)
					.build())
				.build())
			.temperature(temperature)
			.build();
	}

	/** Same, for a model named by the OpenAI enum (as the builder's own overload does, by its value). */
	public static OpenAiChatOptions options(OpenAiApi.ChatModel model, String schemaName, String jsonSchema, boolean strict, double temperature) {
		return options(model.getValue(), schemaName, jsonSchema, strict, temperature);
	}

	/**
	 * The temperature to use with {@code model}: GPT-5-family models only accept the default (1), so a
	 * low, deterministic temperature can only be asked of the others.
	 */
	public static double temperatureFor(String model, double preferred) {
		return model != null && model.startsWith("gpt-5") ? 1.0 : preferred;
	}
}
