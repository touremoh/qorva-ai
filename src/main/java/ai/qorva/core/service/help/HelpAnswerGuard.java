package ai.qorva.core.service.help;

import ai.qorva.core.dto.HelpData;
import ai.qorva.core.utils.SupportedLanguages;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deterministic checks on every Qorva Help answer before it reaches the user — the backstop for an injection
 * that got past the prompt. A leak of the instructions is replaced by a fixed answer; links, images, HTML,
 * web addresses, email addresses (except support's) and secret-shaped strings are removed; page links are
 * limited to {@link HelpLinks#ALLOWED}. Nothing here logs answer content, only reason codes.
 */
public final class HelpAnswerGuard {

	static final int MAX_ANSWER_CHARS = 4000;
	static final int MAX_LINKS = 3;
	static final int MAX_FOLLOW_UPS = 3;
	static final int MAX_FOLLOW_UP_CHARS = 120;
	/** A verbatim run of the instructions this long in an answer counts as a leak. */
	static final int ECHO_WINDOW = 80;
	private static final int ECHO_STEP = 20;

	private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)]\\([^)]*\\)");
	private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
	private static final Pattern LINK_DEFINITION = Pattern.compile("(?m)^\\s*\\[[^\\]]+]:\\s*\\S+.*$");
	private static final Pattern HTML_TAG = Pattern.compile("</?[A-Za-z][^>]*>");
	private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?|ftp|file|javascript|data|vbscript|mailto):[^\\s)\\]>]*|\\bwww\\.[^\\s)\\]>]+");
	private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final List<Pattern> SECRETS = List.of(
		Pattern.compile("\\bsk-[A-Za-z0-9_-]{10,}"),
		Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
		Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"),
		Pattern.compile("(?i)\\bmongodb(?:\\+srv)?://\\S+"),
		Pattern.compile("\\b[A-Z][A-Z0-9_]{2,}_(?:KEY|SECRET|TOKEN|PASSWORD|URL|URI)\\b"),
		Pattern.compile("(?i)\\b(?:whsec|rk_live|sk_live|pk_live|sk_test)_[A-Za-z0-9]{8,}"));

	static final Map<String, String> FALLBACK = Map.of(
		"en", "I can't help with that. I can answer questions about using Qorva — or you can contact support.",
		"fr", "Je ne peux pas vous aider sur ce point. Je réponds aux questions sur l'utilisation de Qorva — vous pouvez aussi contacter le support.",
		"de", "Dabei kann ich nicht helfen. Ich beantworte Fragen zur Nutzung von Qorva – oder Sie wenden sich an den Support.",
		"es", "No puedo ayudarte con eso. Respondo preguntas sobre el uso de Qorva; también puedes contactar con soporte.",
		"it", "Non posso aiutarti su questo. Rispondo a domande sull'uso di Qorva, oppure puoi contattare il supporto.",
		"nl", "Daar kan ik niet mee helpen. Ik beantwoord vragen over het gebruik van Qorva — of neem contact op met support.",
		"pt", "Não posso ajudar com isso. Respondo a perguntas sobre a utilização do Qorva — ou pode contactar o suporte.");

	public record Result(HelpData.ModelAnswer answer, List<String> reasons) {
	}

	private final String canary;
	private final List<String> instructionWindows;
	private final String supportEmail;

	/**
	 * @param instructions the prompt's own instructions (not the help text, which may legitimately be quoted)
	 * @param canary       a random marker present only in the prompt
	 */
	public HelpAnswerGuard(String instructions, String canary, String supportEmail) {
		this.canary = canary;
		this.supportEmail = supportEmail != null ? supportEmail.toLowerCase(Locale.ROOT) : null;
		var normalized = normalize(instructions);
		var windows = new ArrayList<String>();
		for (int i = 0; i + ECHO_WINDOW <= normalized.length(); i += ECHO_STEP) {
			windows.add(normalized.substring(i, i + ECHO_WINDOW));
		}
		this.instructionWindows = List.copyOf(windows);
	}

	public Result check(HelpData.ModelAnswer raw, String language) {
		var reasons = new ArrayList<String>();
		var answer = raw != null && raw.answer() != null ? raw.answer() : "";
		if (leaks(answer) || (raw != null && raw.followUps() != null && raw.followUps().stream().anyMatch(this::leaks))) {
			reasons.add("prompt_echo");
			return new Result(fallback(language), reasons);
		}
		answer = clean(answer, reasons);
		if (answer.isBlank()) {
			reasons.add("empty");
			return new Result(fallback(language), reasons);
		}
		if (answer.length() > MAX_ANSWER_CHARS) {
			reasons.add("truncated");
			answer = answer.substring(0, MAX_ANSWER_CHARS) + "…";
		}
		var links = new LinkedHashSet<String>();
		if (raw.links() != null) {
			for (var key : raw.links()) {
				if (HelpLinks.isAllowed(key)) links.add(key);
				else reasons.add("link_dropped");
			}
		}
		var followUps = new ArrayList<String>();
		if (raw.followUps() != null) {
			for (var q : raw.followUps()) {
				var cleaned = q == null ? "" : clean(q, new ArrayList<>()).replaceAll("[*_`#>]", "").replaceAll("\\s+", " ").trim();
				if (!cleaned.isEmpty() && cleaned.length() <= MAX_FOLLOW_UP_CHARS && followUps.size() < MAX_FOLLOW_UPS) {
					followUps.add(cleaned);
				}
			}
		}
		return new Result(new HelpData.ModelAnswer(answer, links.stream().limit(MAX_LINKS).toList(), followUps, raw.offerSupport()),
			reasons);
	}

	public HelpData.ModelAnswer fallback(String language) {
		return new HelpData.ModelAnswer(FALLBACK.get(SupportedLanguages.normalize(language)), List.of(), List.of(), true);
	}

	boolean leaks(String text) {
		if (text == null || text.isEmpty()) return false;
		if (canary != null && text.contains(canary)) return true;
		var normalized = normalize(text);
		if (normalized.length() < ECHO_WINDOW) return false;
		for (var window : instructionWindows) {
			if (normalized.contains(window)) return true;
		}
		return false;
	}

	String clean(String text, List<String> reasons) {
		var out = replace(text, IMAGE, "$1", "image", reasons);
		out = replace(out, LINK, "$1", "link", reasons);
		out = replace(out, LINK_DEFINITION, "", "link", reasons);
		out = replace(out, HTML_TAG, "", "html", reasons);
		out = replace(out, URL, "", "url", reasons);
		for (var secret : SECRETS) {
			out = replace(out, secret, "[removed]", "secret", reasons);
		}
		var emails = EMAIL.matcher(out);
		var sb = new StringBuilder();
		while (emails.find()) {
			var keep = supportEmail != null && emails.group().toLowerCase(Locale.ROOT).equals(supportEmail);
			if (!keep) reasons.add("email");
			emails.appendReplacement(sb, keep ? emails.group().replace("$", "\\$") : "");
		}
		emails.appendTail(sb);
		return sb.toString().replaceAll("[ \\t]{2,}", " ").strip();
	}

	private static String replace(String text, Pattern pattern, String replacement, String reason, List<String> reasons) {
		var matcher = pattern.matcher(text);
		if (!matcher.find()) return text;
		reasons.add(reason);
		return matcher.replaceAll(replacement);
	}

	private static String normalize(String text) {
		return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
	}
}
