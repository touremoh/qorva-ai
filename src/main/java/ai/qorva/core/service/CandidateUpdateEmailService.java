package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaException;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Sends the candidate self-update invitation. Minimal localized inline template —
 * every send includes the unsubscribe link (suppression list is honored upstream).
 */
@Service
public class CandidateUpdateEmailService extends AbstractEmailService {

	private record Copy(String subject, String greeting, String body, String button, String unsubscribe) {}

	private static final Map<String, Copy> COPY = Map.of(
		"en", new Copy("{tenant} asked you to refresh your profile", "Hello {name},",
			"{tenant} keeps your profile on file and would like to make sure it is still up to date. It takes one minute — availability, salary expectations, and optionally a newer resume.",
			"Update my profile", "If you prefer not to be contacted again, click here to unsubscribe."),
		"fr", new Copy("{tenant} vous invite à actualiser votre profil", "Bonjour {name},",
			"{tenant} conserve votre profil et souhaite s'assurer qu'il est toujours à jour. Cela prend une minute — disponibilité, prétentions salariales et, si vous le souhaitez, un CV plus récent.",
			"Mettre à jour mon profil", "Si vous ne souhaitez plus être contacté, cliquez ici pour vous désinscrire."),
		"de", new Copy("{tenant} bittet Sie, Ihr Profil zu aktualisieren", "Hallo {name},",
			"{tenant} führt Ihr Profil und möchte sicherstellen, dass es noch aktuell ist. Es dauert eine Minute — Verfügbarkeit, Gehaltsvorstellung und optional ein neuerer Lebenslauf.",
			"Profil aktualisieren", "Wenn Sie nicht mehr kontaktiert werden möchten, klicken Sie hier, um sich abzumelden."),
		"es", new Copy("{tenant} le invita a actualizar su perfil", "Hola {name},",
			"{tenant} conserva su perfil y quiere asegurarse de que sigue actualizado. Solo toma un minuto — disponibilidad, expectativa salarial y, opcionalmente, un currículum más reciente.",
			"Actualizar mi perfil", "Si prefiere no ser contactado de nuevo, haga clic aquí para darse de baja."),
		"it", new Copy("{tenant} ti invita ad aggiornare il tuo profilo", "Ciao {name},",
			"{tenant} conserva il tuo profilo e vuole assicurarsi che sia ancora aggiornato. Richiede un minuto — disponibilità, aspettative salariali e, facoltativamente, un curriculum più recente.",
			"Aggiorna il mio profilo", "Se preferisci non essere più contattato, clicca qui per annullare l'iscrizione."),
		"nl", new Copy("{tenant} vraagt u uw profiel bij te werken", "Hallo {name},",
			"{tenant} bewaart uw profiel en wil zeker weten dat het nog actueel is. Het duurt één minuut — beschikbaarheid, salarisverwachting en optioneel een nieuwer cv.",
			"Mijn profiel bijwerken", "Wilt u niet meer benaderd worden, klik dan hier om u af te melden."),
		"pt", new Copy("{tenant} convida-o a atualizar o seu perfil", "Olá {name},",
			"{tenant} mantém o seu perfil e gostaria de garantir que continua atualizado. Demora um minuto — disponibilidade, expectativa salarial e, opcionalmente, um currículo mais recente.",
			"Atualizar o meu perfil", "Se preferir não voltar a ser contactado, clique aqui para cancelar a subscrição."));

	public CandidateUpdateEmailService(OAuth2TokenService oauth2TokenService) {
		super(oauth2TokenService);
	}

	@Override
	public void send(ai.qorva.core.dto.UserDTO user, String language) {
		// Not applicable — this service targets candidates, not platform users.
		throw new UnsupportedOperationException("Use sendUpdateInvitation");
	}

	public void sendUpdateInvitation(String recipientEmail, String candidateName, String tenantName,
		String updateLink, String language) throws QorvaException {
		var copy = COPY.getOrDefault(language != null ? language : "en", COPY.get("en"));
		var name = candidateName != null && !candidateName.isBlank() ? candidateName : "";
		var tenant = tenantName != null ? tenantName : "Qorva";

		var subject = copy.subject().replace("{tenant}", tenant);
		var html = """
			<div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;color:#0f172a;">
			  <p>%s</p>
			  <p style="color:#475569;line-height:1.6;">%s</p>
			  <p style="text-align:center;margin:28px 0;">
			    <a href="%s" style="background:#629C44;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:8px;font-weight:bold;">%s</a>
			  </p>
			  <p style="color:#94a3b8;font-size:12px;line-height:1.5;">
			    <a href="%s" style="color:#94a3b8;">%s</a>
			  </p>
			</div>
			""".formatted(
			copy.greeting().replace("{name}", name),
			copy.body().replace("{tenant}", tenant),
			updateLink,
			copy.button(),
			updateLink + "?unsubscribe=true",
			copy.unsubscribe());

		sendEmail(recipientEmail, subject, html);
	}
}
