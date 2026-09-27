package ai.qorva.core.service.orchestrators;

import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** What every AI-summary agent does the same way: load its prompt, ask for JSON, report a failure as 503. */
final class InsightSupport {

	private InsightSupport() {
	}

	static String readPrompt(String file) throws QorvaException {
		try (var reader = new BufferedReader(new InputStreamReader(
			new ClassPathResource("prompts/" + file).getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining("\n"));
		} catch (Exception e) {
			throw new QorvaException("Cannot read prompt " + file, e);
		}
	}

	/** One structured call at a low temperature (the model's default where it allows no other). */
	static <T> T call(ChatClient chatClient, ObjectMapper objectMapper, String model,
	                  String prompt, String schemaName, Class<T> type) throws Exception {
		var converter = new BeanOutputConverter<>(type);
		var content = chatClient.prompt()
			.options(StructuredOutput.options(model, schemaName, converter.getJsonSchema(), false,
				StructuredOutput.temperatureFor(model, 0.2)))
			.messages(new UserMessage(prompt))
			.call()
			.content();
		return objectMapper.readValue(content, type);
	}

	static QorvaException unavailable(String key, Throwable cause) {
		return QorvaErrors.of(key, cause, HttpStatus.SERVICE_UNAVAILABLE);
	}
}
