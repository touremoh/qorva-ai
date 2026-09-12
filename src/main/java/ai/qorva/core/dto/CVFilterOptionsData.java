package ai.qorva.core.dto;

import java.util.List;

/**
 * Distinct values the CV list filters are built from, for one tenant.
 *
 * Every option carries the number of CVs holding that value, so the UI can show
 * "Senior (52)" and the user knows in advance how many rows a pick will return.
 * Enum facets keep a {@code value == null} bucket for CVs that were never analysed;
 * the value facets drop it.
 */
public record CVFilterOptionsData(
	List<Option> seniority,
	List<Option> leadership,
	List<Option> availability,
	List<Option> skillDepth,
	List<Option> industries,
	List<Option> locations,
	List<Option> skills,
	List<Option> tags,
	List<Option> sources,
	ExperienceRange experience
) {
	public record Option(String value, long count) {}

	/** Min/max years of experience present in the library, null when no CV has a career start year. */
	public record ExperienceRange(Integer min, Integer max) {}

	public static CVFilterOptionsData empty() {
		return new CVFilterOptionsData(
			List.of(), List.of(), List.of(), List.of(), List.of(),
			List.of(), List.of(), List.of(), List.of(), new ExperienceRange(null, null));
	}
}
