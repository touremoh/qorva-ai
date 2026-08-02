package ai.qorva.core.service;

import ai.qorva.core.service.CandidateUpdateEmailService.CustomTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CandidateUpdateEmailServiceTest {

	@Mock
	private OAuth2TokenService oauth2TokenService;

	@Mock
	private EmailSenderResolver senderResolver;

	private CandidateUpdateEmailService service;

	private static final String LINK = "https://app.qorva.ai/candidate-update/tok123";

	@BeforeEach
	void setUp() {
		service = new CandidateUpdateEmailService(oauth2TokenService, senderResolver);
	}

	@Test
	void buildInvitation_defaultCopy_keepsLocalizedSubjectAndShell() {
		var rendered = service.buildInvitation("John Doe", "Acme", LINK, "en", null, null);

		assertThat(rendered.subject()).isEqualTo("Acme asked you to refresh your profile");
		assertThat(rendered.html())
			.contains("Hello John Doe,")
			.contains(LINK)
			.contains("?unsubscribe=true")
			.contains("Update my profile");
	}

	@Test
	void buildInvitation_customTemplate_substitutesPlaceholders() {
		var custom = new CustomTemplate(
			"{{company_name}} wants news from {{candidate_name}}",
			"Hi {{candidate_name}}, we at {{company_name}} would love an update.");

		var rendered = service.buildInvitation("Jane", "Acme", LINK, "en", custom, null);

		assertThat(rendered.subject()).isEqualTo("Acme wants news from Jane");
		assertThat(rendered.html())
			.contains("Hi Jane, we at Acme would love an update.")
			.contains(LINK)                     // button link stays application-owned
			.contains("?unsubscribe=true");     // unsubscribe footer always present
	}

	@Test
	void buildInvitation_customBody_htmlIsEscaped() {
		var custom = new CustomTemplate("Subject", "Hello <script>alert('x')</script> & <b>friends</b>");

		var rendered = service.buildInvitation("Jane", "Acme", LINK, "en", custom, null);

		assertThat(rendered.html())
			.doesNotContain("<script>")
			.doesNotContain("<b>friends</b>")
			.contains("&lt;script&gt;")
			.contains("&amp;");
	}

	@Test
	void buildInvitation_placeholderValues_areEscaped() {
		var custom = new CustomTemplate("Subject", "Hi {{candidate_name}}");

		var rendered = service.buildInvitation("<img src=x>", "Acme", LINK, "en", custom, null);

		assertThat(rendered.html())
			.doesNotContain("<img src=x>")
			.contains("&lt;img src=x&gt;");
	}

	@Test
	void buildInvitation_blankLinesBecomeParagraphs_newlinesBecomeBreaks() {
		var custom = new CustomTemplate("Subject", "First paragraph\nsame paragraph\n\nSecond paragraph");

		var rendered = service.buildInvitation("Jane", "Acme", LINK, "en", custom, null);

		assertThat(rendered.html())
			.contains("First paragraph<br>same paragraph")
			.contains("Second paragraph");
		assertThat(rendered.html().split("Second paragraph")[0])
			.containsPattern("<p [^>]*>First paragraph");
	}

	@Test
	void buildInvitation_unknownLanguage_fallsBackToEnglishShell() {
		var rendered = service.buildInvitation("Jane", "Acme", LINK, "xx", null, null);

		assertThat(rendered.subject()).isEqualTo("Acme asked you to refresh your profile");
	}

	@Test
	void buildInvitation_signature_includesClosingSenderAndCompany() {
		var rendered = service.buildInvitation("Jane", "Logicasoft LLC", LINK, "en", null, "Mohamed Toure");

		var html = rendered.html();
		assertThat(html).contains("Best regards,").contains("Mohamed Toure").contains("Logicasoft LLC");
		// Structure: closing before name, name before company.
		assertThat(html.indexOf("Best regards,")).isLessThan(html.indexOf("Mohamed Toure"));
		assertThat(html.indexOf("Mohamed Toure")).isLessThan(html.lastIndexOf("Logicasoft LLC"));
	}

	@Test
	void buildInvitation_signature_isLocalizedAndPresentForCustomTemplates() {
		var custom = new CustomTemplate("Subject", "Corps du message");

		var rendered = service.buildInvitation("Jane", "Acme", LINK, "fr", custom, "Mohamed");

		assertThat(rendered.html()).contains("Cordialement,").contains("Mohamed");
	}

	@Test
	void buildInvitation_unknownSender_skipsNameLineKeepsCompany() {
		var rendered = service.buildInvitation("Jane", "Acme", LINK, "en", null, null);

		assertThat(rendered.html()).contains("Best regards,");
		assertThat(rendered.html()).doesNotContain("<span style=\"font-weight:bold;color:#0f172a;\"></span>");
	}

	@Test
	void buildInvitation_senderName_isEscaped() {
		var rendered = service.buildInvitation("Jane", "Acme", LINK, "en", null, "<b>Evil</b>");

		assertThat(rendered.html()).doesNotContain("<b>Evil</b>").contains("&lt;b&gt;Evil&lt;/b&gt;");
	}
}
