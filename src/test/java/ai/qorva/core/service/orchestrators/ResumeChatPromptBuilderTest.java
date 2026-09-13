package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dto.ScreeningContext;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeChatPromptBuilderTest {

	@Test
	void quotesTheOfficialScoreWhenAReportExists() {
		var ctx = new ScreeningContext("{cv}", "{job}", "{report}", "r1", 64.0, false);

		String block = ResumeChatPromptBuilder.contextBlock(ctx);

		assertThat(block).contains("official screening score: 64%").contains("do not compute your own").contains("{report}");
		assertThat(block).doesNotContain("none generated yet");
	}

	@Test
	void tellsTheModelNotToScoreWhenThereIsNoReport() {
		var ctx = new ScreeningContext("{cv}", "{job}", null);

		String block = ResumeChatPromptBuilder.contextBlock(ctx);

		assertThat(block).contains("RESUME MATCH ANALYSIS: none generated yet. Do not state a fit percentage or score.");
	}

	@Test
	void flagsAReportOlderThanTheCv() {
		var ctx = new ScreeningContext("{cv}", "{job}", "{report}", "r1", 71.5, true);

		assertThat(ResumeChatPromptBuilder.reportHeader(ctx))
			.contains("71.5%").contains("the CV was updated after this report was generated");
	}

	@Test
	void systemMessagesCarryTheScoringAndFormattingRules() {
		var messages = ResumeChatPromptBuilder.build(new ScreeningContext("{cv}", "{job}", null), null, List.of(), "fr");

		assertThat(messages).hasSize(2).allMatch(m -> m instanceof SystemMessage);
		assertThat(messages.get(0).getText())
			.contains("Never estimate, compute or invent a percentage")
			.contains("Markdown is rendered")
			.contains("Answer in this language: fr.");
	}
}
