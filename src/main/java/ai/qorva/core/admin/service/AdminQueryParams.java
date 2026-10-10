package ai.qorva.core.admin.service;

import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaErrors;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.utils.Paging;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Parsing of the list filters the console sends: paging, date bounds (instant or plain date), plain values. */
public final class AdminQueryParams {

	private AdminQueryParams() {
	}

	static int page(Map<String, String> params) {
		return Paging.page(Paging.param(params, "page", 0));
	}

	static int size(Map<String, String> params) {
		return Paging.size(Paging.param(params, "size", 25));
	}

	/** {@code 2026-10-15T21:59:59Z} or {@code 2026-10-15} (start of that UTC day); null when absent. */
	public static Instant instant(Map<String, String> params, String name) throws QorvaException {
		var value = params.get(name);
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return value.length() <= 10 ? LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.parse(value);
		} catch (DateTimeParseException e) {
			throw QorvaErrors.badRequest(QorvaErrorCodes.HTTP_VALIDATION);
		}
	}

	/** Adds {@code field ∈ [from, to]} for the {@code from}/{@code to} params. */
	static void createdBetween(List<Criteria> criteria, Map<String, String> params, String field) throws QorvaException {
		var from = instant(params, "from");
		var to = instant(params, "to");
		if (from != null && to != null) {
			criteria.add(Criteria.where(field).gte(from).lte(to));
		} else if (from != null) {
			criteria.add(Criteria.where(field).gte(from));
		} else if (to != null) {
			criteria.add(Criteria.where(field).lte(to));
		}
	}

	static void equalsIfPresent(List<Criteria> criteria, Map<String, String> params, String name, String field) {
		var value = params.get(name);
		if (StringUtils.hasText(value)) {
			criteria.add(Criteria.where(field).is(value.trim()));
		}
	}
}
