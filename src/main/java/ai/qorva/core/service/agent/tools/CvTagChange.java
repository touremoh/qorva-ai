package ai.qorva.core.service.agent.tools;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.agent.AgentToolResult;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Shared by add_cv_tags and remove_cv_tags: validates the arguments, then updates each CV through
 * {@link CVService#updateOne} — the path the resume list's PATCH takes, so validation, auditing (the
 * user, from the run's scope) and side effects are the same as a manual edit.
 */
final class CvTagChange {

	static final int MAX_CVS = 25;
	static final int MAX_TAGS = 5;
	static final int MAX_TAG_LENGTH = 40;

	static final String SCHEMA = """
		{"type":"object","properties":{
		  "cvIds":{"type":"array","items":{"type":"string"},"minItems":1,"maxItems":25},
		  "tags":{"type":"array","items":{"type":"string","maxLength":40},"minItems":1,"maxItems":5}
		},"required":["cvIds","tags"],"additionalProperties":false}""";

	private CvTagChange() {
	}

	/**
	 * @param change current tags + requested tags → new tags, or null when nothing changes
	 */
	static AgentToolResult apply(CVService cvService, JsonNode args, String summaryKey,
	                             BiFunction<List<String>, List<String>, List<String>> change) throws QorvaException {
		var cvIds = ToolArgs.list(args, "cvIds").stream().distinct().toList();
		var tags = ToolArgs.list(args, "tags").stream()
			.map(t -> t.replaceAll("\\s+", " ").strip())
			.filter(t -> !t.isEmpty())
			.distinct()
			.toList();
		if (cvIds.isEmpty() || tags.isEmpty()) return AgentToolResult.error("cvIds and tags are required");
		if (cvIds.size() > MAX_CVS) return AgentToolResult.error("At most " + MAX_CVS + " CVs per call");
		if (tags.size() > MAX_TAGS) return AgentToolResult.error("At most " + MAX_TAGS + " tags per call");
		if (tags.stream().anyMatch(t -> t.length() > MAX_TAG_LENGTH)) {
			return AgentToolResult.error("Tags are at most " + MAX_TAG_LENGTH + " characters");
		}

		var changed = new ArrayList<AgentRun.Link>();
		var unchanged = new ArrayList<String>();
		var notFound = new ArrayList<String>();
		for (var id : cvIds) {
			CVDTO cv;
			try {
				cv = cvService.findOneById(id);
			} catch (QorvaException e) {
				notFound.add(id);
				continue;
			}
			var current = cv.getTags() != null ? cv.getTags() : List.<String>of();
			var next = change.apply(current, tags);
			if (next == null) {
				unchanged.add(id);
				continue;
			}
			var patch = new CVDTO();
			patch.setTags(new ArrayList<>(next));
			cvService.updateOne(cv.getId(), patch);
			changed.add(new AgentRun.Link("CV", cv.getId(), CvProjections.name(cv)));
		}

		var data = new LinkedHashMap<String, Object>();
		data.put("updated", changed.stream().map(AgentRun.Link::getId).toList());
		data.put("alreadyInThatState", unchanged);
		data.put("notFound", notFound);
		return AgentToolResult.ok(data, summaryKey,
			Map.of("count", String.valueOf(changed.size()), "tags", String.join(", ", tags)), changed);
	}

	/** Adds the tags missing from the CV, compared case-insensitively; null if all are already there. */
	static List<String> added(List<String> current, List<String> tags) {
		var lower = current.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
		var missing = tags.stream().filter(t -> !lower.contains(t.toLowerCase(Locale.ROOT))).toList();
		if (missing.isEmpty()) return null;
		var next = new ArrayList<>(current);
		next.addAll(missing);
		return next;
	}

	/** Removes the tags, compared case-insensitively; null if none of them is on the CV. */
	static List<String> removed(List<String> current, List<String> tags) {
		var lower = tags.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
		var next = current.stream().filter(t -> !lower.contains(t.toLowerCase(Locale.ROOT))).toList();
		return next.size() == current.size() ? null : next;
	}
}
