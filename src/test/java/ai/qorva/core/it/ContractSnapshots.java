package ai.qorva.core.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-file snapshots of API responses: status plus the full body, with the values that change
 * on every run normalised away. ObjectIds become {@code <id:n>} numbered by first appearance (so
 * a response that links two documents still shows the link), UUIDs {@code <uuid>}, timestamps
 * with a time part {@code <ts>}, JWTs {@code <jwt>}.
 *
 * <p>Goldens live in {@code src/test/resources/contracts}. Run with {@code -Dcontracts.record=true}
 * to (re)write them — only when a change to the API is intended, and review the diff.</p>
 */
public final class ContractSnapshots {

	private static final Path DIR = Path.of("src/test/resources/contracts");
	private static final Path ACTUAL_DIR = Path.of("target/contract-actual");
	private static final boolean RECORD = Boolean.getBoolean("contracts.record");
	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	private static final Pattern OBJECT_ID = Pattern.compile("[0-9a-f]{24}");
	private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
	private static final Pattern TIMESTAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?(Z|[+-]\\d{2}:?\\d{2})?");
	/** Numeric fields that carry an absolute time (epoch millis), so they differ on every run. */
	private static final java.util.Set<String> EPOCH_FIELDS = java.util.Set.of("expires_in", "expiresAt", "resendAvailableAt");
	private static final Pattern JWT = Pattern.compile("[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}");

	private ContractSnapshots() {
	}

	public static void assertMatches(String name, int status, String body) {
		assertMatches(name, status, body, Map.of());
	}

	/**
	 * @param rankedArrays top-level body arrays the API orders by a count but leaves ties in
	 *                     database order (field name → the count it is ranked by). Ties are put in a
	 *                     canonical order before comparing, so the ranking is still checked but the
	 *                     order among equal counts is not.
	 */
	public static void assertMatches(String name, int status, String body, Map<String, String> rankedArrays) {
		var actual = JsonNodeFactory.instance.objectNode();
		actual.put("status", status);
		var parsed = parse(body);
		rankedArrays.forEach((field, spec) -> canonicaliseTies(parsed.get(field), spec));
		actual.set("body", normalise(parsed, new HashMap<>()));
		var canonical = sortKeys(actual);
		var file = DIR.resolve(name + ".json");
		try {
			if (RECORD || !Files.exists(file)) {
				if (!RECORD) {
					throw new AssertionError("No golden file " + file + " — run once with -Dcontracts.record=true and review it");
				}
				Files.createDirectories(DIR);
				Files.writeString(file, MAPPER.writeValueAsString(canonical) + "\n");
				return;
			}
			var expected = sortKeys(MAPPER.readTree(file.toFile()));
			if (!expected.equals(canonical)) {
				// Side-by-side copy for review: diff src/test/resources/contracts target/contract-actual
				Files.createDirectories(ACTUAL_DIR);
				Files.writeString(ACTUAL_DIR.resolve(name + ".json"), MAPPER.writeValueAsString(canonical) + "\n");
			}
			assertThat(MAPPER.writeValueAsString(canonical))
				.as("API contract drift for %s (golden %s)", name, file)
				.isEqualTo(MAPPER.writeValueAsString(expected));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Key order is not part of a JSON contract; sort it so goldens and diffs are stable. */
	private static JsonNode sortKeys(JsonNode node) {
		if (node.isObject()) {
			var sorted = new java.util.TreeMap<String, JsonNode>();
			node.fields().forEachRemaining(e -> sorted.put(e.getKey(), sortKeys(e.getValue())));
			var out = JsonNodeFactory.instance.objectNode();
			sorted.forEach(out::set);
			return out;
		}
		if (node.isArray()) {
			ArrayNode out = JsonNodeFactory.instance.arrayNode();
			node.forEach(child -> out.add(sortKeys(child)));
			return out;
		}
		return node;
	}

	/**
	 * {@code spec} is the rank field, optionally followed by {@code ",cutoff"} for a top-N list: then
	 * the last group of equal ranks is dropped too, because which tied items make the cut is arbitrary.
	 */
	private static void canonicaliseTies(JsonNode array, String spec) {
		if (array == null || !array.isArray()) {
			return;
		}
		var parts = spec.split(",");
		var rankField = parts[0];
		var items = new java.util.ArrayList<JsonNode>();
		array.forEach(items::add);
		items.sort(java.util.Comparator
			.comparingDouble((JsonNode n) -> -n.path(rankField).asDouble())
			.thenComparing(JsonNode::toString));
		if (parts.length > 1 && "cutoff".equals(parts[1]) && !items.isEmpty()) {
			var lastRank = items.getLast().path(rankField).asDouble();
			items.removeIf(n -> n.path(rankField).asDouble() == lastRank);
		}
		var target = (ArrayNode) array;
		target.removeAll();
		items.forEach(target::add);
	}

	private static JsonNode parse(String body) {
		if (body == null || body.isBlank()) {
			return JsonNodeFactory.instance.nullNode();
		}
		try {
			return MAPPER.readTree(body);
		} catch (IOException notJson) {
			return TextNode.valueOf(body);
		}
	}

	private static JsonNode normalise(JsonNode node, Map<String, String> ids) {
		if (node.isObject()) {
			var out = JsonNodeFactory.instance.objectNode();
			Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
			while (fields.hasNext()) {
				var field = fields.next();
				if (field.getValue().isNumber() && EPOCH_FIELDS.contains(field.getKey())) {
					out.put(field.getKey(), "<epoch>");
				} else {
					out.set(field.getKey(), normalise(field.getValue(), ids));
				}
			}
			return out;
		}
		if (node.isArray()) {
			ArrayNode out = JsonNodeFactory.instance.arrayNode();
			node.forEach(child -> out.add(normalise(child, ids)));
			return out;
		}
		if (node.isTextual()) {
			return TextNode.valueOf(normaliseText(node.asText(), ids));
		}
		return node;
	}

	private static String normaliseText(String text, Map<String, String> ids) {
		var out = JWT.matcher(text).replaceAll("<jwt>");
		out = UUID.matcher(out).replaceAll("<uuid>");
		out = TIMESTAMP.matcher(out).replaceAll("<ts>");
		var matcher = OBJECT_ID.matcher(out);
		var sb = new StringBuilder();
		while (matcher.find()) {
			var label = ids.computeIfAbsent(matcher.group(), k -> "<id:" + (ids.size() + 1) + ">");
			matcher.appendReplacement(sb, label);
		}
		matcher.appendTail(sb);
		return sb.toString();
	}
}
