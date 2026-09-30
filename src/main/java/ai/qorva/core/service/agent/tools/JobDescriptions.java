package ai.qorva.core.service.agent.tools;

import org.springframework.web.util.HtmlUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

/** The job editor stores HTML; model-written plain text becomes escaped paragraphs (then sanitised by the service). */
final class JobDescriptions {

	static final int MAX_LENGTH = 20_000;

	private JobDescriptions() {
	}

	static String toHtml(String plainText) {
		return Arrays.stream(plainText.strip().split("\\R\\s*\\R"))
			.map(p -> "<p>" + HtmlUtils.htmlEscape(p.strip()).replaceAll("\\R", "<br>") + "</p>")
			.collect(Collectors.joining());
	}
}
