package ai.qorva.core.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContactNormalizerTest {

	@Test
	void email_ignoresCaseAndSurroundingSpaces() {
		assertThat(ContactNormalizer.emailKey(" Jane.Doe@Example.COM ")).isEqualTo("jane.doe@example.com");
		assertThat(ContactNormalizer.emailKey("jane.doe@example.com")).isEqualTo("jane.doe@example.com");
	}

	@Test
	void email_withoutAnAtSign_orBlank_hasNoKey() {
		assertThat(ContactNormalizer.emailKey("not an email")).isNull();
		assertThat(ContactNormalizer.emailKey("  ")).isNull();
		assertThat(ContactNormalizer.emailKey(null)).isNull();
	}

	@Test
	void internationalPhones_matchWhateverTheFormatting() {
		assertThat(ContactNormalizer.phoneKey("+32 470 12 34 56", null)).isEqualTo("+32470123456");
		assertThat(ContactNormalizer.phoneKey("0032 470 12 34 56", null)).isEqualTo("+32470123456");
		assertThat(ContactNormalizer.phoneKey("+32-470-12-34-56", null)).isEqualTo("+32470123456");
		assertThat(ContactNormalizer.phoneKey("+32 (0)470 12 34 56", null)).isEqualTo("+32470123456");
	}

	@Test
	void localPhones_useTheResumeCountry_givenAsCodeOrName() {
		assertThat(ContactNormalizer.phoneKey("0470 12 34 56", "BE")).isEqualTo("+32470123456");
		assertThat(ContactNormalizer.phoneKey("0470/12.34.56", "Belgique")).isEqualTo("+32470123456");
		assertThat(ContactNormalizer.phoneKey("0470123456", "belgium")).isEqualTo("+32470123456");
		assertThat(ContactNormalizer.phoneKey("06 12 34 56 78", "France")).isEqualTo("+33612345678");
		assertThat(ContactNormalizer.phoneKey("(415) 555-0100", "United States")).isEqualTo("+14155550100");
		assertThat(ContactNormalizer.phoneKey("020 7946 0958", "UK")).isEqualTo("+442079460958");
	}

	@Test
	void localPhones_withoutAKnownCountry_matchOnlyTheSameDigits() {
		assertThat(ContactNormalizer.phoneKey("0470 12 34 56", null)).isEqualTo("local:0470123456");
		assertThat(ContactNormalizer.phoneKey("0470-123-456", "Atlantis")).isEqualTo("local:0470123456");
	}

	@Test
	void tooShortOrBlankPhones_haveNoKey() {
		assertThat(ContactNormalizer.phoneKey("123", "BE")).isNull();
		assertThat(ContactNormalizer.phoneKey("ext. 42", null)).isNull();
		assertThat(ContactNormalizer.phoneKey(" ", null)).isNull();
	}

	@Test
	void regionOf_readsCodesAndNamesInTheAppLanguages() {
		assertThat(ContactNormalizer.regionOf("fr")).isEqualTo("FR");
		assertThat(ContactNormalizer.regionOf("Deutschland")).isEqualTo("DE");
		assertThat(ContactNormalizer.regionOf("België")).isEqualTo("BE");
		assertThat(ContactNormalizer.regionOf("España")).isEqualTo("ES");
		assertThat(ContactNormalizer.regionOf("USA")).isEqualTo("US");
		assertThat(ContactNormalizer.regionOf("Narnia")).isNull();
	}

	@Test
	void keys_areAbsentWhenThereIsNothingToCompare() {
		assertThat(ContactNormalizer.keysOf(null, null, "BE")).isNull();
		var keys = ContactNormalizer.keysOf("A@B.io", null, null);
		assertThat(keys.getEmail()).isEqualTo("a@b.io");
		assertThat(keys.getPhone()).isNull();
	}
}
