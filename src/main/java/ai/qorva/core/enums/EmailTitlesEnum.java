package ai.qorva.core.enums;

import ai.qorva.core.exception.QorvaException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum EmailTitlesEnum {

	EN_REGISTRATION_SUCCESS("en", "registration", "Welcome to Qorva: Registration Successful"),
	FR_REGISTRATION_SUCCESS("fr", "registration", "Bienvenue chez Qorva : Inscription réussie"),
	DE_REGISTRATION_SUCCESS("de", "registration", "Willkommen bei Qorva: Registrierung erfolgreich"),
	IT_REGISTRATION_SUCCESS("it", "registration", "Benvenuto su Qorva: Registrazione completata"),
	ES_REGISTRATION_SUCCESS("es", "registration", "Bienvenido a Qorva: Registro exitoso"),
	PT_REGISTRATION_SUCCESS("pt", "registration", "Bem-vindo ao Qorva: Registro bem-sucedido"),
	NL_REGISTRATION_SUCCESS("nl", "registration", "Welkom bij Qorva: Registratie geslaagd"),

	EN_SUBSCRIPTION_WELCOME("en", "subscription", "Your Qorva AI subscription is active"),
	FR_SUBSCRIPTION_WELCOME("fr", "subscription", "Votre abonnement Qorva AI est actif"),
	DE_SUBSCRIPTION_WELCOME("de", "subscription", "Ihr Qorva AI-Abonnement ist aktiv"),
	IT_SUBSCRIPTION_WELCOME("it", "subscription", "Il tuo abbonamento Qorva AI è attivo"),
	ES_SUBSCRIPTION_WELCOME("es", "subscription", "Tu suscripción a Qorva AI está activa"),
	PT_SUBSCRIPTION_WELCOME("pt", "subscription", "A sua subscrição Qorva AI está ativa"),
	NL_SUBSCRIPTION_WELCOME("nl", "subscription", "Uw Qorva AI-abonnement is actief"),

	EN_USER_ADDED("en", "user_added", "You've been added to Qorva AI"),
	FR_USER_ADDED("fr", "user_added", "Vous avez été ajouté à Qorva AI"),
	DE_USER_ADDED("de", "user_added", "Sie wurden zu Qorva AI hinzugefügt"),
	IT_USER_ADDED("it", "user_added", "Sei stato aggiunto a Qorva AI"),
	ES_USER_ADDED("es", "user_added", "Has sido añadido a Qorva AI"),
	PT_USER_ADDED("pt", "user_added", "Foi adicionado ao Qorva AI"),
	NL_USER_ADDED("nl", "user_added", "U bent toegevoegd aan Qorva AI"),

	EN_DEMO_WELCOME("en", "demo_welcome", "Welcome to Qorva AI — activate your account"),
	FR_DEMO_WELCOME("fr", "demo_welcome", "Bienvenue chez Qorva AI — activez votre compte"),
	DE_DEMO_WELCOME("de", "demo_welcome", "Willkommen bei Qorva AI — aktivieren Sie Ihr Konto"),
	IT_DEMO_WELCOME("it", "demo_welcome", "Benvenuto su Qorva AI — attiva il tuo account"),
	ES_DEMO_WELCOME("es", "demo_welcome", "Bienvenido a Qorva AI — activa tu cuenta"),
	PT_DEMO_WELCOME("pt", "demo_welcome", "Bem-vindo ao Qorva AI — ative a sua conta"),
	NL_DEMO_WELCOME("nl", "demo_welcome", "Welkom bij Qorva AI — activeer uw account"),

	EN_PASSWORD_RESET("en", "password_reset", "Reset your Qorva AI password"),
	FR_PASSWORD_RESET("fr", "password_reset", "Réinitialisez votre mot de passe Qorva AI"),
	DE_PASSWORD_RESET("de", "password_reset", "Setzen Sie Ihr Qorva AI-Passwort zurück"),
	IT_PASSWORD_RESET("it", "password_reset", "Reimposta la tua password Qorva AI"),
	ES_PASSWORD_RESET("es", "password_reset", "Restablece tu contraseña de Qorva AI"),
	PT_PASSWORD_RESET("pt", "password_reset", "Redefina a sua palavra-passe Qorva AI"),
	NL_PASSWORD_RESET("nl", "password_reset", "Stel uw Qorva AI-wachtwoord opnieuw in"),

	EN_MFA_CODE("en", "mfa_code", "Your Qorva AI verification code"),
	FR_MFA_CODE("fr", "mfa_code", "Votre code de vérification Qorva AI"),
	DE_MFA_CODE("de", "mfa_code", "Ihr Qorva AI-Bestätigungscode"),
	IT_MFA_CODE("it", "mfa_code", "Il tuo codice di verifica Qorva AI"),
	ES_MFA_CODE("es", "mfa_code", "Tu código de verificación de Qorva AI"),
	PT_MFA_CODE("pt", "mfa_code", "O seu código de verificação Qorva AI"),
	NL_MFA_CODE("nl", "mfa_code", "Uw Qorva AI-verificatiecode");

	EmailTitlesEnum(String languageCode, String emailType, String emailTitle) {
		this.languageCode = languageCode;
		this.emailType = emailType;
		this.emailTitle = emailTitle;
	}

	public static String getEmailTitle(String languageCode) throws QorvaException {
		return getEmailTitle(languageCode, "registration");
	}

	public static String getEmailTitle(String languageCode, String emailType) throws QorvaException {
		for (EmailTitlesEnum e : EmailTitlesEnum.values()) {
			if (e.getLanguageCode().equals(languageCode) && e.emailType.equals(emailType)) {
				return e.getEmailTitle();
			}
		}
		throw new QorvaException(
			"Unknown language code " + languageCode + " or email type " + emailType,
			HttpStatus.INTERNAL_SERVER_ERROR.value(),
			HttpStatus.INTERNAL_SERVER_ERROR
		);
	}

	private final String languageCode;
	private final String emailType;
	private final String emailTitle;
}
