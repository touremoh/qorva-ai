package ai.qorva.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "templates.cv")
public class CVTemplateProperties {
	private Map<String, Map<String, String>> translations = new HashMap<>();

	public Map<String, String> getLanguageSectionTitles(String languageCode) {
		return translations.getOrDefault(languageCode, translations.getOrDefault("en", Map.of()));
	}

	public String getValue(String languageCode, String key) {
		return getLanguageSectionTitles(languageCode).get(key);
	}
}
