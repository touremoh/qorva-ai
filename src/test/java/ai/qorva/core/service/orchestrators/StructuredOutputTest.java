package ai.qorva.core.service.orchestrators;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.ResponseFormat;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredOutputTest {

	@Test
	void buildsAJsonSchemaResponseFormat() {
		var options = StructuredOutput.options("gpt-4.1-mini", "cv_parser", "{\"type\":\"object\"}", true, 0.1);

		assertThat(options.getModel()).isEqualTo("gpt-4.1-mini");
		assertThat(options.getTemperature()).isEqualTo(0.1);
		var format = options.getResponseFormat();
		assertThat(format.getType()).isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
		assertThat(format.getJsonSchema().getName()).isEqualTo("cv_parser");
		assertThat(format.getJsonSchema().getSchema()).containsEntry("type", "object");
		assertThat(format.getJsonSchema().getStrict()).isTrue();
	}

	@Test
	void gpt5ModelsOnlyGetTheDefaultTemperature() {
		assertThat(StructuredOutput.temperatureFor("gpt-5.6-terra", 0.1)).isEqualTo(1.0);
		assertThat(StructuredOutput.temperatureFor("gpt-4.1-mini", 0.1)).isEqualTo(0.1);
		assertThat(StructuredOutput.temperatureFor(null, 0.3)).isEqualTo(0.3);
	}
}
