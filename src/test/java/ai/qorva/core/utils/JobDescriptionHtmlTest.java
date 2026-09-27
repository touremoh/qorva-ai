package ai.qorva.core.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobDescriptionHtmlTest {

	@Test
	void keepsQuillMarkup() {
		var quill = "<h2>Role</h2><p class=\"ql-align-center\"><strong>Own</strong> the <em>platform</em>.</p>"
			+ "<ul><li class=\"ql-indent-1\">Java</li></ul><p><a href=\"https://qorva.ai\" target=\"_blank\" rel=\"noopener noreferrer\">Us</a></p>";
		assertThat(JobDescriptionHtml.sanitize(quill)).isEqualTo(quill);
	}

	@Test
	void removesScriptsHandlersImagesAndScriptLinks() {
		var dirty = "<p onclick=\"steal()\">Hi<script>alert(1)</script></p><img src=x onerror=alert(1)>"
			+ "<a href=\"javascript:alert(1)\">x</a>";
		assertThat(JobDescriptionHtml.sanitize(dirty)).isEqualTo("<p>Hi</p><a>x</a>");
	}

	@Test
	void leavesPlainTextAlone() {
		var text = "Salary < 60k & equity.\nRemote.";
		assertThat(JobDescriptionHtml.sanitize(text)).isEqualTo(text);
		assertThat(JobDescriptionHtml.sanitize(null)).isNull();
	}
}
