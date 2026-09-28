package ai.qorva.core.utils;

import ai.qorva.core.dto.common.ContactKeys;
import ai.qorva.core.dto.common.PersonalInformation;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the contact details extracted from a resume into comparison keys for duplicate detection.
 * <ul>
 *   <li>Email: trimmed and lower-cased.</li>
 *   <li>Phone: E.164 through libphonenumber. An international number ({@code +} or {@code 00}) needs no
 *       country; a local one is read with the resume's own address country. When that is unknown the
 *       key is {@code local:<digits>}, so identical local numbers still match each other.</li>
 * </ul>
 */
public final class ContactNormalizer {

	static final String LOCAL_PREFIX = "local:";
	private static final int MIN_DIGITS = 7;
	private static final String UNKNOWN_REGION = "ZZ";
	private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();

	/** The app's languages: extracted country names can be in any of them. */
	private static final List<Locale> NAME_LOCALES = List.of(Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN,
		Locale.forLanguageTag("es"), Locale.forLanguageTag("pt"), Locale.ITALIAN, Locale.forLanguageTag("nl"));
	private static final Map<String, String> REGION_BY_NAME = regionsByName();

	private ContactNormalizer() {
	}

	/** Keys for a resume's contact, or null when it has neither an email nor a phone key. */
	public static ContactKeys keysOf(PersonalInformation info) {
		var contact = info != null ? info.getContact() : null;
		if (contact == null) return null;
		var country = contact.getAddress() != null ? contact.getAddress().getCountry() : null;
		return keysOf(contact.getEmail(), contact.getPhone(), country);
	}

	/** Same, from raw values (the backfill reads documents, not DTOs). */
	public static ContactKeys keysOf(String email, String phone, String country) {
		var emailKey = emailKey(email);
		var phoneKey = phoneKey(phone, country);
		return emailKey == null && phoneKey == null ? null : new ContactKeys(emailKey, phoneKey);
	}

	public static String emailKey(String raw) {
		if (raw == null) return null;
		var email = raw.trim().toLowerCase(Locale.ROOT);
		return email.isEmpty() || !email.contains("@") ? null : email;
	}

	/**
	 * @param country the resume's address country, as extracted: an ISO code ("BE") or a name ("Belgique"); may be null
	 */
	public static String phoneKey(String raw, String country) {
		if (raw == null || raw.isBlank()) return null;
		var digits = raw.replaceAll("\\D", "");
		if (digits.length() < MIN_DIGITS) return null;

		var trimmed = raw.trim();
		String international = trimmed.startsWith("+") ? trimmed
			: trimmed.startsWith("00") ? "+" + trimmed.substring(2) : null;
		var e164 = international != null ? e164(international, UNKNOWN_REGION) : null;
		if (e164 == null && international == null) {
			var region = regionOf(country);
			if (region != null) e164 = e164(trimmed, region);
		}
		return e164 != null ? e164 : LOCAL_PREFIX + digits;
	}

	/** ISO 3166-1 alpha-2 region for an extracted country (code or name in the app's languages), or null. */
	public static String regionOf(String country) {
		if (country == null || country.isBlank()) return null;
		var value = country.trim();
		if (value.length() == 2) {
			var code = value.toUpperCase(Locale.ROOT);
			if (PHONES.getSupportedRegions().contains(code)) return code;
		}
		return REGION_BY_NAME.get(fold(value));
	}

	private static String e164(String number, String region) {
		try {
			var parsed = PHONES.parse(number, region);
			return PHONES.isPossibleNumber(parsed) ? PHONES.format(parsed, PhoneNumberFormat.E164) : null;
		} catch (NumberParseException e) {
			return null;
		}
	}

	private static Map<String, String> regionsByName() {
		var names = new HashMap<String, String>();
		for (var region : PHONES.getSupportedRegions()) {
			var locale = new Locale.Builder().setRegion(region).build();
			for (var language : NAME_LOCALES) {
				names.putIfAbsent(fold(locale.getDisplayCountry(language)), region);
			}
		}
		// Common short forms the display names don't cover.
		names.putIfAbsent("usa", "US");
		names.putIfAbsent("uk", "GB");
		names.putIfAbsent("england", "GB");
		return Map.copyOf(names);
	}

	/** Lower-case, no accents, no punctuation: "Belgique", "belgique " and "BELGIQUE" read the same. */
	private static String fold(String name) {
		return Normalizer.normalize(name, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z]", "");
	}
}
