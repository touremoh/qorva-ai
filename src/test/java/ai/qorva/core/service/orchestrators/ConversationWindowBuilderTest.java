package ai.qorva.core.service.orchestrators;

import ai.qorva.core.dao.entity.ChatMessage;
import ai.qorva.core.enums.ChatUserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationWindowBuilderTest {

	/** 400 chars ≈ 100 estimated tokens per message. */
	private static ChatMessage msg(int i, ChatUserRole role) {
		return ChatMessage.builder()
			.id("m" + i)
			.role(role)
			.content(("x".repeat(399) + i).substring(0, 400))
			.createdAt(Instant.ofEpochSecond(i))
			.build();
	}

	private static List<ChatMessage> transcript(int n) {
		return IntStream.range(0, n)
			.mapToObj(i -> msg(i, i % 2 == 0 ? ChatUserRole.USER : ChatUserRole.ASSISTANT))
			.toList();
	}

	@Test
	void keepsNewestMessagesWithinTheBudget() {
		var window = new ConversationWindowBuilder(350, 2).select(transcript(10));

		assertThat(window.sent()).extracting(ChatMessage::getId).containsExactly("m7", "m8", "m9");
		assertThat(window.dropped()).hasSize(7);
		assertThat(window.sentTokens()).isEqualTo(300);
	}

	@Test
	void alwaysKeepsTheRecentMinimumEvenWhenOverBudget() {
		var window = new ConversationWindowBuilder(10, 4).select(transcript(10));

		assertThat(window.sent()).extracting(ChatMessage::getId).containsExactly("m6", "m7", "m8", "m9");
	}

	@Test
	void sendsEverythingWhenItFits() {
		var window = new ConversationWindowBuilder(100_000, 2).select(transcript(5));

		assertThat(window.sent()).hasSize(5);
		assertThat(window.dropped()).isEmpty();
	}

	@Test
	void ignoresSystemRowsAndHandlesEmptyHistory() {
		var withSystem = List.of(msg(0, ChatUserRole.SYSTEM), msg(1, ChatUserRole.USER));

		assertThat(new ConversationWindowBuilder(1000, 2).select(withSystem).sent())
			.extracting(ChatMessage::getId).containsExactly("m1");
		assertThat(new ConversationWindowBuilder(1000, 2).select(List.of()).sent()).isEmpty();
		assertThat(ConversationWindowBuilder.estimateTokens(withSystem)).isEqualTo(100);
	}
}
