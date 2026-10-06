package ai.qorva.core.service.orchestrators;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationWindowBuilderTest {

	/** 400 chars ≈ 100 estimated tokens per turn; the turn's number ends its text. */
	private static ConversationTurn turn(int i) {
		var text = ("x".repeat(399) + i).substring(0, 400 - String.valueOf(i).length()) + i;
		return new ConversationTurn(i % 2 == 0, text);
	}

	private static List<ConversationTurn> transcript(int n) {
		return IntStream.range(0, n).mapToObj(ConversationWindowBuilderTest::turn).toList();
	}

	private static List<String> numbers(List<ConversationTurn> turns) {
		return turns.stream().map(t -> t.text().replaceAll("^x+", "")).toList();
	}

	@Test
	void keepsNewestTurnsWithinTheBudget() {
		var window = new ConversationWindowBuilder(350, 2).select(transcript(10));

		assertThat(numbers(window.sent())).containsExactly("7", "8", "9");
		assertThat(window.dropped()).hasSize(7);
		assertThat(window.sentTokens()).isEqualTo(300);
	}

	@Test
	void alwaysKeepsTheRecentMinimumEvenWhenOverBudget() {
		var window = new ConversationWindowBuilder(10, 4).select(transcript(10));

		assertThat(numbers(window.sent())).containsExactly("6", "7", "8", "9");
	}

	@Test
	void sendsEverythingWhenItFits() {
		var window = new ConversationWindowBuilder(100_000, 2).select(transcript(5));

		assertThat(window.sent()).hasSize(5);
		assertThat(window.dropped()).isEmpty();
	}

	@Test
	void ignoresBlankTurnsAndHandlesEmptyHistory() {
		var withBlank = List.of(ConversationTurn.assistant(" "), turn(1));

		assertThat(numbers(new ConversationWindowBuilder(1000, 2).select(withBlank).sent())).containsExactly("1");
		assertThat(new ConversationWindowBuilder(1000, 2).select(List.of()).sent()).isEmpty();
	}
}
