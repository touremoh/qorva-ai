package ai.qorva.core.admin.dto;

import java.util.List;
import java.util.function.Function;

/** One page of an admin list: 0-based {@code page}, {@code size} 1–100. */
public record AdminPage<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> AdminPage<T> of(List<T> content, int page, int size, long total) {
		return new AdminPage<>(content, page, size, total, size == 0 ? 0 : (int) ((total + size - 1) / size));
	}

	public <R> AdminPage<R> map(Function<T, R> mapper) {
		return new AdminPage<>(content.stream().map(mapper).toList(), page, size, totalElements, totalPages);
	}
}
