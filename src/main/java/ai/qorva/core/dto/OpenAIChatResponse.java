package ai.qorva.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenAIChatResponse(@JsonProperty(required = true, value = "content") String content) {
}
