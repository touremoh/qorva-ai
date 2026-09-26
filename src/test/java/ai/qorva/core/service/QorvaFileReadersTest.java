package ai.qorva.core.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class QorvaFileReadersTest {

	@Test
	void thePdfsModificationDateIsItsDocumentDate() throws Exception {
		var modified = calendar("2025-03-14T09:30:00Z");
		var created = calendar("2024-01-02T08:00:00Z");

		var date = QorvaFileReaders.PDF_READER.readDocumentDate(pdf(created, modified));

		assertThat(date).isEqualTo(Instant.parse("2025-03-14T09:30:00Z"));
	}

	@Test
	void withoutAModificationDateTheCreationDateIsUsed_andWithoutEitherNothing() throws Exception {
		assertThat(QorvaFileReaders.PDF_READER.readDocumentDate(pdf(calendar("2024-01-02T08:00:00Z"), null)))
			.isEqualTo(Instant.parse("2024-01-02T08:00:00Z"));
		assertThat(QorvaFileReaders.PDF_READER.readDocumentDate(pdf(null, null))).isNull();
	}

	@Test
	void anUnreadableFileHasNoDate() {
		assertThat(QorvaFileReaders.PDF_READER.readDocumentDate(
			new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[]{1, 2, 3}))).isNull();
	}

	private static MockMultipartFile pdf(Calendar created, Calendar modified) throws Exception {
		try (var document = new PDDocument(); var out = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			document.getDocumentInformation().setCreationDate(created);
			document.getDocumentInformation().setModificationDate(modified);
			document.save(out);
			return new MockMultipartFile("file", "cv.pdf", "application/pdf", out.toByteArray());
		}
	}

	private static Calendar calendar(String instant) {
		var calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		calendar.setTimeInMillis(Instant.parse(instant).toEpochMilli());
		return calendar;
	}
}
