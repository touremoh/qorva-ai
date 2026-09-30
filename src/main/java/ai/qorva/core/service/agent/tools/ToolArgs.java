package ai.qorva.core.service.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Lenient readers for model-supplied arguments: wrong types read as absent, numbers are clamped. */
final class ToolArgs {

	private ToolArgs() {
	}

	static String text(JsonNode args, String field) {
		var node = args == null ? null : args.get(field);
		if (node == null || node.isNull()) return null;
		var value = node.asText().trim();
		return value.isEmpty() ? null : value;
	}

	static List<String> list(JsonNode args, String field) {
		var node = args == null ? null : args.get(field);
		var values = new ArrayList<String>();
		if (node == null || node.isNull()) return values;
		if (node.isArray()) {
			node.forEach(v -> {
				var s = v.asText().trim();
				if (!s.isEmpty()) values.add(s);
			});
		} else if (!node.asText().isBlank()) {
			values.add(node.asText().trim());
		}
		return values;
	}

	/** Comma-separated, for the list query params; commas inside values are dropped. */
	static String csv(JsonNode args, String field) {
		var values = list(args, field).stream().map(v -> v.replace(",", " ")).toList();
		return values.isEmpty() ? null : String.join(",", values);
	}

	static int integer(JsonNode args, String field, int fallback, int min, int max) {
		var node = args == null ? null : args.get(field);
		int value = node != null && node.canConvertToInt() ? node.asInt() : fallback;
		return Math.max(min, Math.min(max, value));
	}

	static Integer optionalInteger(JsonNode args, String field) {
		var node = args == null ? null : args.get(field);
		return node != null && node.canConvertToInt() ? node.asInt() : null;
	}

	static String truncate(String value, int max) {
		if (value == null || value.length() <= max) return value;
		return value.substring(0, max) + "…";
	}
}
