package ai.qorva.core.utils;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.enums.ContentDateSourceEnum;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Derives a CV's {@code contentDate} — the best evidence of when its content was current.
 * Freshness must be content-based: a 3-year-old CV bulk-imported today is NOT fresh, so
 * {@code lastUpdatedAt} is never used here. Evidence, strongest date wins:
 * dates parsed from work history / education / certifications, and the document's own
 * metadata date (set upstream from the uploaded file). Human verification (VERIFIED) is
 * set by explicit user actions and is never overridden by this resolver.
 */
public final class CVContentDateResolver {

	private CVContentDateResolver() {}

	private static final int MIN_PLAUSIBLE_YEAR = 1950;

	// "03/2023", "2023-03", "March 2023", bare "2023"…
	private static final Pattern MONTH_SLASH_YEAR = Pattern.compile("\\b(\\d{1,2})[/.\\-](\\d{4})\\b");
	private static final Pattern YEAR_DASH_MONTH = Pattern.compile("\\b(\\d{4})[/.\\-](\\d{1,2})\\b");
	private static final Pattern MONTH_NAME_YEAR = Pattern.compile("\\b([A-Za-z]{3,9})\\.?\\s+(\\d{4})\\b");
	private static final Pattern BARE_YEAR = Pattern.compile("\\b(19[5-9]\\d|20\\d{2})\\b");

	/**
	 * Computes contentDate/contentDateSource on the DTO before persistence.
	 * Keeps any pre-set value (document metadata, verification) when it is the stronger evidence.
	 */
	public static void resolve(CVDTO dto) {
		if (dto == null) {
			return;
		}
		// Explicit human/candidate verification always wins — never downgrade it.
		if (ContentDateSourceEnum.VERIFIED.name().equals(dto.getContentDateSource()) && dto.getContentDate() != null) {
			return;
		}

		Instant workHistoryDate = latestParsedDate(dto);
		Instant preSet = dto.getContentDate();

		if (workHistoryDate != null && (preSet == null || workHistoryDate.isAfter(preSet))) {
			dto.setContentDate(workHistoryDate);
			dto.setContentDateSource(ContentDateSourceEnum.WORK_HISTORY.name());
		} else if (preSet != null) {
			if (!StringUtils.hasText(dto.getContentDateSource())) {
				dto.setContentDateSource(ContentDateSourceEnum.DOC_METADATA.name());
			}
		} else {
			dto.setContentDate(null);
			dto.setContentDateSource(ContentDateSourceEnum.UNKNOWN.name());
		}
	}

	private static Instant latestParsedDate(CVDTO dto) {
		List<String> candidates = new ArrayList<>();
		if (dto.getWorkExperience() != null) {
			dto.getWorkExperience().forEach(we -> {
				candidates.add(we.getFrom());
				candidates.add(we.getTo());
			});
		}
		if (dto.getEducation() != null) {
			dto.getEducation().forEach(e -> candidates.add(e.getYear()));
		}
		if (dto.getCertifications() != null) {
			dto.getCertifications().forEach(c -> candidates.add(c.getYear()));
		}

		Instant latest = null;
		for (String candidate : candidates) {
			Instant parsed = parseLatest(candidate);
			if (parsed != null && (latest == null || parsed.isAfter(latest))) {
				latest = parsed;
			}
		}
		return latest;
	}

	/**
	 * Extracts the latest plausible date mentioned in a free-form date string.
	 * "Present"/"current" markers carry no evidence of when the CV was written and yield null.
	 * Year-only evidence resolves to the END of the period (a role ending "2023" proves the CV
	 * is at best from some point in 2023). Future dates are capped at now.
	 */
	static Instant parseLatest(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		var text = raw.trim();
		int maxYear = Year.now(ZoneOffset.UTC).getValue() + 1;
		Instant now = Instant.now();

		Instant best = null;

		var monthSlashYear = MONTH_SLASH_YEAR.matcher(text);
		while (monthSlashYear.find()) {
			best = later(best, toInstant(parseInt(monthSlashYear.group(2)), parseInt(monthSlashYear.group(1)), maxYear));
		}
		var yearDashMonth = YEAR_DASH_MONTH.matcher(text);
		while (yearDashMonth.find()) {
			best = later(best, toInstant(parseInt(yearDashMonth.group(1)), parseInt(yearDashMonth.group(2)), maxYear));
		}
		var monthNameYear = MONTH_NAME_YEAR.matcher(text);
		while (monthNameYear.find()) {
			Integer month = monthFromName(monthNameYear.group(1));
			if (month != null) {
				best = later(best, toInstant(parseInt(monthNameYear.group(2)), month, maxYear));
			}
		}
		if (best == null) {
			var bareYear = BARE_YEAR.matcher(text);
			while (bareYear.find()) {
				int year = parseInt(bareYear.group(1));
				if (year >= MIN_PLAUSIBLE_YEAR && year <= maxYear) {
					best = later(best, LocalDate.of(year, Month.DECEMBER, 31).atStartOfDay(ZoneOffset.UTC).toInstant());
				}
			}
		}

		if (best != null && best.isAfter(now)) {
			best = now;
		}
		return best;
	}

	private static Instant toInstant(int year, int month, int maxYear) {
		if (year < MIN_PLAUSIBLE_YEAR || year > maxYear || month < 1 || month > 12) {
			return null;
		}
		return YearMonth.of(year, month).atEndOfMonth().atStartOfDay(ZoneOffset.UTC).toInstant();
	}

	private static Integer monthFromName(String name) {
		var normalized = name.toLowerCase(Locale.ENGLISH);
		for (Month month : Month.values()) {
			var full = month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ENGLISH);
			var abbrev = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toLowerCase(Locale.ENGLISH);
			if (normalized.equals(full) || normalized.equals(abbrev) || full.startsWith(normalized)) {
				return month.getValue();
			}
		}
		return null;
	}

	private static Instant later(Instant a, Instant b) {
		if (a == null) return b;
		if (b == null) return a;
		return b.isAfter(a) ? b : a;
	}

	private static int parseInt(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
