package ai.qorva.core.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccountCreationInviteTemplateTest {

	private static String template(String lang) throws Exception {
		return new ClassPathResource("templates/emails/" + lang + "_user_added_content.html").getContentAsString(StandardCharsets.UTF_8);
	}

	@Test
	void everyLanguageOffersBothInvitesAndNoPassword() throws Exception {
		for (var lang : List.of("en", "fr", "de", "es", "it", "nl", "pt")) {
			var html = template(lang);
			assertThat(html).as(lang).doesNotContain("temporary_password")
				.contains("<!--SET_PASSWORD-->", "<!--/SET_PASSWORD-->", "{{set_password_url}}", "<!--SSO-->", "<!--/SSO-->", "{{sign_in_url}}");
		}
	}

	@Test
	void oneInviteKeepsItsBlockAndDropsTheOther() throws Exception {
		var html = template("en");

		var byLink = AccountCreationNotificationService.keepSection(html, "SET_PASSWORD", "SSO");
		assertThat(byLink).contains("{{set_password_url}}").doesNotContain("{{sign_in_url}}", "<!--");

		var bySso = AccountCreationNotificationService.keepSection(html, "SSO", "SET_PASSWORD");
		assertThat(bySso).contains("{{sign_in_url}}", "Sign in with Microsoft").doesNotContain("{{set_password_url}}", "<!--");
	}
}
