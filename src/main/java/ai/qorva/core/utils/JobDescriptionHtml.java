package ai.qorva.core.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;

/**
 * Job descriptions are Quill HTML written in the app, or plain text from seeds and ATS imports.
 * HTML is cleaned before it is stored, so no consumer (the app, exports, prompts) ever receives
 * script, event handlers, {@code javascript:} links or remote images; the browser still sanitises
 * again on display. Plain text is stored untouched: the app escapes it itself, and running it
 * through an HTML cleaner would turn "&" and "<" into entities that then show up literally.
 */
public final class JobDescriptionHtml {

	/** Anything that looks like a tag, comment or doctype. */
	private static final Pattern LOOKS_LIKE_HTML = Pattern.compile("<[a-zA-Z!/]");

	private static final Safelist SAFELIST = Safelist.relaxed()
		.removeTags("img")
		// Quill keeps alignment, indentation and code blocks in classes; text direction in dir.
		.addAttributes(":all", "class", "dir")
		.addAttributes("a", "target", "rel")
		.addProtocols("a", "href", "http", "https", "mailto");

	private static final Document.OutputSettings OUTPUT = new Document.OutputSettings().prettyPrint(false);

	private JobDescriptionHtml() {
	}

	public static String sanitize(String description) {
		if (description == null || !LOOKS_LIKE_HTML.matcher(description).find()) {
			return description;
		}
		return Jsoup.clean(description, "", SAFELIST, OUTPUT);
	}
}
