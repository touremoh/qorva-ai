package ai.qorva.core.service;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.enums.EmailCategory;
import ai.qorva.core.enums.EmailTitlesEnum;
import ai.qorva.core.enums.MfaPurpose;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Sends email MFA codes. Called synchronously by {@link MfaService} rather than through the
 * pending-email queue: the queue drains once a minute and would persist the code in clear.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "qorva.notifications", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MfaNotificationService extends AbstractEmailService {

	private static final Set<String> LANGUAGES = Set.of("en", "fr", "de", "it", "es", "pt", "nl");

	/** The one sentence that differs per purpose; the rest of the email is the per-language template. */
	private static final Map<String, Map<MfaPurpose, String>> INTRO_LINES = Map.of(
		"en", Map.of(
			MfaPurpose.LOGIN, "Use this code to finish signing in to <strong>Qorva AI</strong>.",
			MfaPurpose.ENABLE, "Use this code to turn on two-step verification for your <strong>Qorva AI</strong> account.",
			MfaPurpose.DISABLE, "Use this code to turn off two-step verification for your <strong>Qorva AI</strong> account."),
		"fr", Map.of(
			MfaPurpose.LOGIN, "Utilisez ce code pour terminer votre connexion à <strong>Qorva AI</strong>.",
			MfaPurpose.ENABLE, "Utilisez ce code pour activer la vérification en deux étapes de votre compte <strong>Qorva AI</strong>.",
			MfaPurpose.DISABLE, "Utilisez ce code pour désactiver la vérification en deux étapes de votre compte <strong>Qorva AI</strong>."),
		"de", Map.of(
			MfaPurpose.LOGIN, "Verwenden Sie diesen Code, um Ihre Anmeldung bei <strong>Qorva AI</strong> abzuschließen.",
			MfaPurpose.ENABLE, "Verwenden Sie diesen Code, um die Zwei-Schritt-Verifizierung für Ihr <strong>Qorva AI</strong>-Konto zu aktivieren.",
			MfaPurpose.DISABLE, "Verwenden Sie diesen Code, um die Zwei-Schritt-Verifizierung für Ihr <strong>Qorva AI</strong>-Konto zu deaktivieren."),
		"it", Map.of(
			MfaPurpose.LOGIN, "Usa questo codice per completare l'accesso a <strong>Qorva AI</strong>.",
			MfaPurpose.ENABLE, "Usa questo codice per attivare la verifica in due passaggi del tuo account <strong>Qorva AI</strong>.",
			MfaPurpose.DISABLE, "Usa questo codice per disattivare la verifica in due passaggi del tuo account <strong>Qorva AI</strong>."),
		"es", Map.of(
			MfaPurpose.LOGIN, "Usa este código para terminar de iniciar sesión en <strong>Qorva AI</strong>.",
			MfaPurpose.ENABLE, "Usa este código para activar la verificación en dos pasos de tu cuenta de <strong>Qorva AI</strong>.",
			MfaPurpose.DISABLE, "Usa este código para desactivar la verificación en dos pasos de tu cuenta de <strong>Qorva AI</strong>."),
		"pt", Map.of(
			MfaPurpose.LOGIN, "Use este código para concluir o início de sessão no <strong>Qorva AI</strong>.",
			MfaPurpose.ENABLE, "Use este código para ativar a verificação em dois passos da sua conta <strong>Qorva AI</strong>.",
			MfaPurpose.DISABLE, "Use este código para desativar a verificação em dois passos da sua conta <strong>Qorva AI</strong>."),
		"nl", Map.of(
			MfaPurpose.LOGIN, "Gebruik deze code om het inloggen bij <strong>Qorva AI</strong> te voltooien.",
			MfaPurpose.ENABLE, "Gebruik deze code om verificatie in twee stappen voor uw <strong>Qorva AI</strong>-account aan te zetten.",
			MfaPurpose.DISABLE, "Gebruik deze code om verificatie in twee stappen voor uw <strong>Qorva AI</strong>-account uit te zetten.")
	);

	@Value("${qorva.assets.logo-url}")
	private String logoUrl;

	@Autowired
	public MfaNotificationService(OAuth2TokenService oauth2TokenService, EmailSenderResolver senderResolver) {
		super(oauth2TokenService, senderResolver);
	}

	@Override
	protected EmailCategory getCategory() {
		return EmailCategory.SECURITY;
	}

	/** Not used: MFA emails always carry a code, see {@link #sendCode}. */
	@Override
	public void send(UserDTO user, String languageCode) {
		throw new UnsupportedOperationException("Use sendCode");
	}

	public void sendCode(User receiver, String code, MfaPurpose purpose, long ttlMinutes) throws QorvaException {
		String lang = resolveLang(receiver.getCommunicationLanguage());
		try {
			String wrapper = loadHtmlTemplate("templates/emails/user-added-template.html");
			String content = loadHtmlTemplate("templates/emails/" + lang + "_mfa_code_content.html");

			content = content
				.replace("{{logo_url}}", logoUrl)
				.replace("{{first_name}}", Objects.requireNonNullElse(receiver.getFirstName(), ""))
				.replace("{{intro_line}}", INTRO_LINES.get(lang).get(purpose))
				.replace("{{code}}", code)
				.replace("{{ttl_minutes}}", String.valueOf(ttlMinutes))
				.replace("{{app_name}}", "Qorva AI")
				.replace("{{support_email}}", supportEmail())
				.replace("{{current_year}}", String.valueOf(LocalDate.now().getYear()));

			String html = wrapper.replace("{{template_content}}", content);
			sendEmail(receiver.getEmail(), EmailTitlesEnum.getEmailTitle(lang, "mfa_code"), html);
			log.info("MFA code email sent: userId={} purpose={}", receiver.getId(), purpose);
		} catch (IOException | RuntimeException e) {
			// Graph SDK failures surface as runtime exceptions, not MailException.
			log.error("Failed to send MFA code email: userId={} purpose={}", receiver.getId(), purpose, e);
			throw new QorvaException("Failed to send MFA code", e,
				HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private static String resolveLang(String lang) {
		return lang != null && LANGUAGES.contains(lang) ? lang : "en";
	}
}
