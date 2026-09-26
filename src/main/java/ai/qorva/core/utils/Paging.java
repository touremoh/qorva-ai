package ai.qorva.core.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Page parameters as clients send them, made safe: page never below 0, size between 1 and
 * {@link #MAX_PAGE_SIZE} — whatever the client asks for, one request never reads more than that.
 */
public final class Paging {

	public static final int MAX_PAGE_SIZE = 100;

	private Paging() {
	}

	public static int size(int requested) {
		return Math.min(Math.max(requested, 1), MAX_PAGE_SIZE);
	}

	public static int page(int requested) {
		return Math.max(requested, 0);
	}

	public static PageRequest of(int page, int size, Sort sort) {
		return PageRequest.of(page(page), size(size), sort);
	}

	/** An integer query parameter; missing or unparsable falls back to the default instead of failing the request. */
	public static int param(Map<String, String> params, String name, int defaultValue) {
		try {
			var value = params.get(name);
			return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
}
