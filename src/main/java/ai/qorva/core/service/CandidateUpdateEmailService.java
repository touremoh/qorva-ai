package ai.qorva.core.service;

import ai.qorva.core.enums.EmailCategory;
import ai.qorva.core.exception.QorvaException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sends the candidate self-update invitation. The recruiter may supply a custom
 * subject/body ({@link CustomTemplate}, plain text + placeholders); the HTML shell —
 * greeting, action button with the tokenized link, unsubscribe footer — always stays
 * application-owned. Every send includes the unsubscribe link (suppression list is
 * honored upstream).
 */
@Service
public class CandidateUpdateEmailService extends AbstractEmailService {

	/** Recruiter-authored copy, snapshotted on the campaign job. Plain text, never HTML. */
	public record CustomTemplate(String subject, String bodyText) {}

	public record RenderedEmail(String subject, String html) {}

	public static final String PLACEHOLDER_CANDIDATE_NAME = "candidate_name";
	public static final String PLACEHOLDER_COMPANY_NAME = "company_name";

	private record Copy(String subject, String greeting, String body, String button, String unsubscribe, String closing) {}

	private static final Map<String, Copy> COPY = Map.of(
		"en", new Copy("{tenant} asked you to refresh your profile", "Hello {name},",
			"{tenant} keeps your profile on file and would like to make sure it is still up to date. It takes one minute — availability, salary expectations, and optionally a newer resume.",
			"Update my profile", "If you prefer not to be contacted again, click here to unsubscribe.", "Best regards,"),
		"fr", new Copy("{tenant} vous invite à actualiser votre profil", "Bonjour {name},",
			"{tenant} conserve votre profil et souhaite s'assurer qu'il est toujours à jour. Cela prend une minute — disponibilité, prétentions salariales et, si vous le souhaitez, un CV plus récent.",
			"Mettre à jour mon profil", "Si vous ne souhaitez plus être contacté, cliquez ici pour vous désinscrire.", "Cordialement,"),
		"de", new Copy("{tenant} bittet Sie, Ihr Profil zu aktualisieren", "Hallo {name},",
			"{tenant} führt Ihr Profil und möchte sicherstellen, dass es noch aktuell ist. Es dauert eine Minute — Verfügbarkeit, Gehaltsvorstellung und optional ein neuerer Lebenslauf.",
			"Profil aktualisieren", "Wenn Sie nicht mehr kontaktiert werden möchten, klicken Sie hier, um sich abzumelden.", "Mit freundlichen Grüßen,"),
		"es", new Copy("{tenant} le invita a actualizar su perfil", "Hola {name},",
			"{tenant} conserva su perfil y quiere asegurarse de que sigue actualizado. Solo toma un minuto — disponibilidad, expectativa salarial y, opcionalmente, un currículum más reciente.",
			"Actualizar mi perfil", "Si prefiere no ser contactado de nuevo, haga clic aquí para darse de baja.", "Atentamente,"),
		"it", new Copy("{tenant} ti invita ad aggiornare il tuo profilo", "Ciao {name},",
			"{tenant} conserva il tuo profilo e vuole assicurarsi che sia ancora aggiornato. Richiede un minuto — disponibilità, aspettative salariali e, facoltativamente, un curriculum più recente.",
			"Aggiorna il mio profilo", "Se preferisci non essere più contattato, clicca qui per annullare l'iscrizione.", "Cordiali saluti,"),
		"nl", new Copy("{tenant} vraagt u uw profiel bij te werken", "Hallo {name},",
			"{tenant} bewaart uw profiel en wil zeker weten dat het nog actueel is. Het duurt één minuut — beschikbaarheid, salarisverwachting en optioneel een nieuwer cv.",
			"Mijn profiel bijwerken", "Wilt u niet meer benaderd worden, klik dan hier om u af te melden.", "Met vriendelijke groet,"),
		"pt", new Copy("{tenant} convida-o a atualizar o seu perfil", "Olá {name},",
			"{tenant} mantém o seu perfil e gostaria de garantir que continua atualizado. Demora um minuto — disponibilidade, expectativa salarial e, opcionalmente, um currículo mais recente.",
			"Atualizar o meu perfil", "Se preferir não voltar a ser contactado, clique aqui para cancelar a subscrição.", "Com os melhores cumprimentos,"));

	public CandidateUpdateEmailService(OAuth2TokenService oauth2TokenService, EmailSenderResolver senderResolver) {
		super(oauth2TokenService, senderResolver);
	}

	@Override
	protected EmailCategory getCategory() {
		// Candidate-facing update requests are account/profile notifications, not security mail.
		return EmailCategory.UPDATES;
	}

	@Override
	public void send(ai.qorva.core.dto.UserDTO user, String language) {
		// Not applicable — this service targets candidates, not platform users.
		throw new UnsupportedOperationException("Use sendUpdateInvitation");
	}

	public void sendUpdateInvitation(String recipientEmail, String candidateName, String tenantName,
		String updateLink, String language) throws QorvaException {
		sendUpdateInvitation(recipientEmail, candidateName, tenantName, updateLink, language, null, null);
	}

	public void sendUpdateInvitation(String recipientEmail, String candidateName, String tenantName,
		String updateLink, String language, CustomTemplate custom, String senderName) throws QorvaException {
		var rendered = buildInvitation(candidateName, tenantName, updateLink, language, custom, senderName);
		sendEmail(recipientEmail, rendered.subject(), rendered.html());
	}

	/** Also used by template preview/test-send so recruiters see exactly what will be sent. */
	public RenderedEmail buildInvitation(String candidateName, String tenantName,
		String updateLink, String language, CustomTemplate custom, String senderName) {
		var copy = COPY.getOrDefault(language != null ? language : "en", COPY.get("en"));
		var name = candidateName != null && !candidateName.isBlank() ? candidateName : "";
		var tenant = tenantName != null ? tenantName : "Qorva";

		String subject;
		String bodyHtml;
		if (custom != null) {
			subject = substitutePlaceholders(custom.subject(), name, tenant);
			bodyHtml = renderCustomBodyHtml(custom.bodyText(), name, tenant);
		} else {
			subject = copy.subject().replace("{tenant}", tenant);
			bodyHtml = "<p style=\"color:#475569;line-height:1.6;\">"
				+ escapeHtml(copy.body()).replace("{tenant}", escapeHtml(tenant)) + "</p>";
		}

		var html = """
			<div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;color:#0f172a;">
			  <p>%s</p>
			  %s
			  <p style="text-align:center;margin:28px 0;">
			    <a href="%s" style="background:#629C44;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:8px;font-weight:bold;">%s</a>
			  </p>
			  %s
			  <p style="color:#94a3b8;font-size:12px;line-height:1.5;border-top:1px solid #e2e8f0;padding-top:12px;margin-top:24px;">
			    <a href="%s" style="color:#94a3b8;">%s</a>
			  </p>
			</div>
			""".formatted(
			escapeHtml(copy.greeting().replace("{name}", name)),
			bodyHtml,
			updateLink,
			copy.button(),
			renderSignatureHtml(copy.closing(), senderName, tenant),
			updateLink + "?unsubscribe=true",
			copy.unsubscribe());

		return new RenderedEmail(subject, html);
	}

	/**
	 * App-owned signature block: localized closing, sender name, company name.
	 * The name line is skipped when the sender is unknown (e.g. deleted user).
	 */
	private static String renderSignatureHtml(String closing, String senderName, String tenant) {
		var nameLine = senderName != null && !senderName.isBlank()
			? "<span style=\"font-weight:bold;color:#0f172a;\">" + escapeHtml(senderName.trim()) + "</span><br>"
			: "";
		return "<p style=\"color:#475569;line-height:1.7;margin:28px 0 0 0;\">"
			+ escapeHtml(closing) + "<br>"
			+ nameLine
			+ "<span style=\"color:#64748b;\">" + escapeHtml(tenant) + "</span>"
			+ "</p>";
	}

	/**
	 * Escape FIRST, substitute AFTER — recruiter text and placeholder values (candidate
	 * names come from parsed CVs) must both render inert. Blank lines split paragraphs,
	 * single newlines become line breaks.
	 */
	private static String renderCustomBodyHtml(String bodyText, String name, String tenant) {
		var escaped = escapeHtml(bodyText != null ? bodyText : "");
		var substituted = escaped
			.replace("{{" + PLACEHOLDER_CANDIDATE_NAME + "}}", escapeHtml(name))
			.replace("{{" + PLACEHOLDER_COMPANY_NAME + "}}", escapeHtml(tenant));
		return Arrays.stream(substituted.split("\\R{2,}"))
			.filter(p -> !p.isBlank())
			.map(p -> "<p style=\"color:#475569;line-height:1.6;\">" + p.trim().replaceAll("\\R", "<br>") + "</p>")
			.collect(Collectors.joining("\n  "));
	}

	/** Subject is a plain-text header — placeholders substituted, no HTML escaping. */
	private static String substitutePlaceholders(String text, String name, String tenant) {
		return (text != null ? text : "")
			.replace("{{" + PLACEHOLDER_CANDIDATE_NAME + "}}", name)
			.replace("{{" + PLACEHOLDER_COMPANY_NAME + "}}", tenant);
	}

	static String escapeHtml(String value) {
		return value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;");
	}
}
