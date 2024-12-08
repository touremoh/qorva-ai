package ai.qorva.core.enums;

import ai.qorva.core.exception.QorvaException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum EmailTitlesEnum {

	EN_REGISTRATION_SUCCESS("en", "Welcome to Qorva: Registration Successful"),
	FR_REGISTRATION_SUCCESS("fr", "Bienvenue chez Qorva : Inscription réussie"),
	DE_REGISTRATION_SUCCESS("de", "Willkommen bei Qorva: Registrierung erfolgreich"),
	IT_REGISTRATION_SUCCESS("it", "Benvenuto su Qorva: Registrazione completata"),
	ES_REGISTRATION_SUCCESS("es", "Bienvenido a Qorva: Registro exitoso"),
	PT_REGISTRATION_SUCCESS("pt", "Bem-vindo ao Qorva: Registro bem-sucedido"),
	NL_REGISTRATION_SUCCESS("nl", "Welkom bij Qorva: Registratie geslaagd");

	EmailTitlesEnum(String languageCode, String emailTitle) {
		this.languageCode = languageCode;
		this.emailTitle = emailTitle;
	}

	public static String getEmailTitle(String languageCode) throws QorvaException {
		for (EmailTitlesEnum e : EmailTitlesEnum.values()) {
			if (e.getLanguageCode().equals(languageCode)) {
				return e.getEmailTitle();
			}
		}
		throw new QorvaException(
			"Unknown language code " + languageCode,
			HttpStatus.INTERNAL_SERVER_ERROR.value(),
			HttpStatus.INTERNAL_SERVER_ERROR
		);
	}

	private final String languageCode;
	private final String emailTitle;
}
