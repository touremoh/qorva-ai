package ai.qorva.core.service.ats;

/**
 * Cursor convention shared by connectors that page with (updated-after, page-number):
 * the durable cursor stored between runs is a plain ISO-8601 timestamp (or null for
 * "from the beginning"); mid-run continuation tokens are "page:{iso}|{n}". The engine
 * never interprets either — it just replays what the connector handed back.
 */
public final class SyncCursor {

	private SyncCursor() {}

	private static final String PAGE_PREFIX = "page:";

	public record Parsed(String updatedAfter, int page) {}

	public static Parsed parse(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return new Parsed(null, 1);
		}
		if (cursor.startsWith(PAGE_PREFIX)) {
			var raw = cursor.substring(PAGE_PREFIX.length());
			int sep = raw.lastIndexOf('|');
			var ts = raw.substring(0, sep);
			return new Parsed(ts.isBlank() ? null : ts, Integer.parseInt(raw.substring(sep + 1)));
		}
		return new Parsed(cursor, 1);
	}

	public static String pageToken(String updatedAfter, int page) {
		return PAGE_PREFIX + (updatedAfter == null ? "" : updatedAfter) + "|" + page;
	}
}
