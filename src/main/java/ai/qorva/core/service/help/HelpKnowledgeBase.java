package ai.qorva.core.service.help;

import ai.qorva.core.config.AgentProperties;
import ai.qorva.core.config.QorvaProductProperties;
import ai.qorva.core.dto.common.FeatureLimits;
import ai.qorva.core.enums.AtsProviderEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The help text Qorva Help answers from: the articles in {@code help/kb/*.md} (written by us, reviewed in PRs,
 * public-grade — never tenant data or configuration), minus the ones about features switched off here, plus
 * two sections generated from code so they cannot drift: the plan limits and the supported ATS.
 * Assembled once at startup.
 */
@Slf4j
@Component
public class HelpKnowledgeBase {

	static final String LOCATION = "classpath:help/kb/*.md";

	/** ATS names as the app shows them, in the order of {@link AtsProviderEnum}. */
	static final Map<AtsProviderEnum, String> ATS_NAMES = Map.of(
		AtsProviderEnum.GREENHOUSE, "Greenhouse", AtsProviderEnum.RECRUITEE, "Recruitee",
		AtsProviderEnum.WORKABLE, "Workable", AtsProviderEnum.MANATAL, "Manatal",
		AtsProviderEnum.BAMBOOHR, "BambooHR", AtsProviderEnum.ZOHO_RECRUIT, "Zoho Recruit",
		AtsProviderEnum.LEVER, "Lever", AtsProviderEnum.ASHBY, "Ashby");

	public record Article(String file, String title, List<String> routes, List<String> requires, String body) {
	}

	private final List<Article> articles;
	private final String text;
	private final String version;

	@Autowired
	public HelpKnowledgeBase(QorvaProductProperties products, AgentProperties agent) throws IOException {
		this(load(), products, enabledFlags(agent));
	}

	HelpKnowledgeBase(List<Article> all, QorvaProductProperties products, Set<String> enabledFlags) {
		this.articles = all.stream().filter(a -> enabledFlags.containsAll(a.requires())).toList();
		var sb = new StringBuilder();
		for (var article : articles) {
			sb.append(article.body().strip()).append("\n\n");
		}
		sb.append(planLimits(products)).append("\n\n").append(supportedAts());
		this.text = sb.toString().strip();
		this.version = sha256(text).substring(0, 12);
		log.info("Help knowledge base: {} articles, {} chars, version {}", articles.size(), text.length(), version);
	}

	public String text() {
		return text;
	}

	public String version() {
		return version;
	}

	public List<Article> articles() {
		return articles;
	}

	static Set<String> enabledFlags(AgentProperties agent) {
		return agent.isEnabled() && agent.getRules().isEnabled() ? Set.of("agent.rules") : Set.of();
	}

	static List<Article> load() throws IOException {
		var resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
		var list = new ArrayList<Article>();
		for (var resource : Arrays.stream(resources).sorted(Comparator.comparing(Resource::getFilename)).toList()) {
			list.add(parse(resource.getFilename(), resource.getContentAsString(StandardCharsets.UTF_8)));
		}
		return list;
	}

	/** Front matter ({@code routes: [a, b]}, {@code requires: [flag]}) then markdown starting with {@code # Title}. */
	static Article parse(String file, String raw) {
		var content = raw.replace("\r\n", "\n");
		List<String> routes = List.of();
		List<String> requires = List.of();
		if (content.startsWith("---\n")) {
			int end = content.indexOf("\n---", 4);
			if (end < 0) throw new IllegalStateException("Unclosed front matter in " + file);
			for (var line : content.substring(4, end).split("\n")) {
				var trimmed = line.trim();
				if (trimmed.startsWith("routes:")) routes = list(trimmed.substring(7));
				else if (trimmed.startsWith("requires:")) requires = list(trimmed.substring(9));
			}
			content = content.substring(content.indexOf('\n', end + 1) + 1);
		}
		var body = content.strip();
		var firstLine = body.lines().findFirst().orElse("");
		var title = firstLine.startsWith("# ") ? firstLine.substring(2).trim() : file;
		return new Article(file, title, routes, requires, body);
	}

	private static List<String> list(String value) {
		var inner = value.trim().replaceAll("^\\[|]$", "");
		return Arrays.stream(inner.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	static String planLimits(QorvaProductProperties products) {
		var plans = List.of(Map.entry("Starter", products.getStarter()), Map.entry("Pro", products.getPro()),
			Map.entry("Scale", products.getScale()));
		var sb = new StringBuilder("# Plan limits\n\n")
			.append("Limits per billing period, by plan. Demo accounts use the Starter limits. ")
			.append("The Usage page shows the user's own plan, consumption and renewal date.\n\n")
			.append("| | ").append(String.join(" | ", plans.stream().map(Map.Entry::getKey).toList())).append(" |\n")
			.append("|---|---|---|---|\n");
		row(sb, "Users (seats)", plans, p -> p.getFeatures() != null ? p.getFeatures().getSeats() : null);
		row(sb, "Matching actions (screening actions)", plans, p -> limits(p).getScreeningActions());
		row(sb, "Copilot · candidate questions", plans, p -> limits(p).getAiResumeChats());
		row(sb, "Copilot · library analyses", plans, p -> limits(p).getTalentIntelligenceQueries());
		row(sb, "Copilot · tasks", plans, p -> limits(p).getAgentRuns());
		row(sb, "Email templates", plans, p -> limits(p).getEmailTemplates());
		row(sb, "Files per bulk import", plans, p -> limits(p).getBulkUploadFiles());
		row(sb, "ATS connections", plans, p -> limits(p).getAtsConnections());
		row(sb, "Top candidates per job in a matching run (max)", plans, p -> limits(p).getMatchingTopNMax());
		return sb.toString().strip();
	}

	private static FeatureLimits limits(QorvaProductProperties.ProductPlanConfig plan) {
		return plan.getFeatures() != null && plan.getFeatures().getLimits() != null
			? plan.getFeatures().getLimits() : new FeatureLimits();
	}

	private static void row(StringBuilder sb, String label, List<Map.Entry<String, QorvaProductProperties.ProductPlanConfig>> plans,
	                        Function<QorvaProductProperties.ProductPlanConfig, Integer> value) {
		sb.append("| ").append(label);
		for (var plan : plans) {
			var v = plan.getValue() != null ? value.apply(plan.getValue()) : null;
			sb.append(" | ").append(v != null ? String.format(Locale.ENGLISH, "%,d", v) : "—");
		}
		sb.append(" |\n");
	}

	static String supportedAts() {
		var names = Arrays.stream(AtsProviderEnum.values()).map(ATS_NAMES::get).filter(Objects::nonNull).toList();
		return "# Supported ATS\n\nQorva connects to these applicant tracking systems: " + String.join(", ", names)
			+ ". No other ATS is supported today; without an ATS, upload resumes directly in the Resume Library.";
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
