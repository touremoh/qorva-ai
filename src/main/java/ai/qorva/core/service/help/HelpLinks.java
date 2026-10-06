package ai.qorva.core.service.help;

import java.util.Map;

/**
 * The in-app pages Qorva Help may link to, by key. The model only ever names a key; the app maps it to a
 * route (qorva-ai-app {@code features/help/model/helpLinks.js}, same keys). Nothing else becomes a link.
 */
public final class HelpLinks {

	/** Key → what the page is for (shown to the model so it picks the right one). */
	public static final Map<String, String> ALLOWED = Map.ofEntries(
		Map.entry("dashboard", "Dashboard: overview and recruiter metrics"),
		Map.entry("cvs", "Resume Library: upload, search and manage CVs"),
		Map.entry("library-quality", "Data Health: data quality checks and candidate refresh"),
		Map.entry("jobs", "Jobs: create and edit jobs and scoring rules"),
		Map.entry("reports", "Match Reports: run matching and read reports"),
		Map.entry("pipeline", "Pipeline: move candidates between statuses"),
		Map.entry("usage", "Usage: plan meters and consumption"),
		Map.entry("copilot", "Copilot chat"),
		Map.entry("copilot.activity", "Copilot → Activity: tasks and approvals"),
		Map.entry("copilot.rules", "Copilot → Rules: standing rules"),
		Map.entry("configuration.email-templates", "Configuration → Email templates"),
		Map.entry("settings.profile", "Account Settings → My Profile: language, password, two-step verification, connected mailbox"),
		Map.entry("settings.company", "Account Settings → Company: company details, Microsoft sign-in"),
		Map.entry("settings.users", "Account Settings → Users: invite users, permissions"),
		Map.entry("settings.integrations", "Account Settings → Integrations: ATS connections"),
		Map.entry("settings.billing", "Account Settings → Billing: subscription, invoices")
	);

	private HelpLinks() {
	}

	public static boolean isAllowed(String key) {
		return key != null && ALLOWED.containsKey(key);
	}
}
