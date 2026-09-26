package ai.qorva.core.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Every error code has a message in every language the API answers in, and the files stay in step. */
class ErrorMessagesTest {

	private static final List<String> LOCALES = List.of("", "_de", "_es", "_fr", "_it", "_nl", "_pt");

	private static Properties load(String suffix) throws IOException {
		try (InputStream in = ErrorMessagesTest.class.getResourceAsStream("/messages/errors" + suffix + ".properties")) {
			assertThat(in).as("errors%s.properties", suffix).isNotNull();
			var props = new Properties();
			props.load(in);
			return props;
		}
	}

	@Test
	void everyErrorCodeHasAMessageInEveryLanguage() throws IOException {
		var codes = Arrays.stream(QorvaErrorCodes.class.getDeclaredFields())
			.filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == String.class)
			.map(f -> {
				try {
					return (String) f.get(null);
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			})
			.toList();
		for (var locale : LOCALES) {
			var props = load(locale);
			assertThat(codes.stream().filter(code -> !props.containsKey(code)).toList())
				.as("codes missing from errors%s.properties", locale)
				.isEmpty();
		}
	}

	@Test
	void translationsHaveExactlyTheEnglishKeys() throws IOException {
		Set<String> english = new TreeSet<>(load("").stringPropertyNames());
		for (var locale : LOCALES.subList(1, LOCALES.size())) {
			Set<String> keys = new TreeSet<>(load(locale).stringPropertyNames());
			assertThat(keys).as("keys of errors%s.properties", locale).isEqualTo(english);
		}
	}

	@Test
	void placeholderMessageKeepsItsLiteralBraces() throws IOException {
		var pattern = load("").getProperty(QorvaErrorCodes.EMAIL_TEMPLATE_UNKNOWN_PLACEHOLDER);
		assertThat(new MessageFormat(pattern).format(new Object[] {"first_name"}))
			.isEqualTo("Unknown placeholder {{first_name}} — allowed: {{candidate_name}}, {{company_name}}.");
	}
}
