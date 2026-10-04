package ai.qorva.core.service;

import ai.qorva.core.dto.PipelineBoardData;
import ai.qorva.core.enums.ApplicationStatusEnum;
import ai.qorva.core.exception.QorvaException;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineBoardServiceTest {

	private static PipelineBoardData.Card card(String id, Double score, Instant changedAt) {
		return new PipelineBoardData.Card(id, null, null, null, null, score, null, false, "NEW", changedAt, null, null);
	}

	@Test
	void newIsOrderedByScoreAndEveryOtherColumnByItsLatestMove() {
		assertThat(PipelineBoardService.sortField(ApplicationStatusEnum.NEW)).isEqualTo(PipelineBoardService.SCORE);
		assertThat(PipelineBoardService.sortField(ApplicationStatusEnum.HIRED)).isEqualTo(PipelineBoardService.CHANGED_AT);
	}

	@Test
	void aCursorCarriesTheSortValueAndTheIdBackUnchanged() throws QorvaException {
		var id = new ObjectId().toHexString();
		var score = PipelineBoardService.decode(PipelineBoardService.encode(card(id, 82.5, null), ApplicationStatusEnum.NEW), ApplicationStatusEnum.NEW);
		assertThat(score).isEqualTo(new PipelineBoardService.Cursor(82.5, id));

		var at = Instant.parse("2026-10-04T10:00:00Z");
		var date = PipelineBoardService.decode(PipelineBoardService.encode(card(id, null, at), ApplicationStatusEnum.SHORTLISTED),
			ApplicationStatusEnum.SHORTLISTED);
		assertThat(date).isEqualTo(new PipelineBoardService.Cursor(Date.from(at), id));

		var none = PipelineBoardService.decode(PipelineBoardService.encode(card(id, null, null), ApplicationStatusEnum.NEW), ApplicationStatusEnum.NEW);
		assertThat(none.value()).isNull();
	}

	@Test
	void aTamperedCursorIsABadRequest() {
		assertThatThrownBy(() -> PipelineBoardService.decode("bm90LWEtY3Vyc29y", ApplicationStatusEnum.NEW))
			.isInstanceOf(QorvaException.class);
	}
}
