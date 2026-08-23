package ai.qorva.core.utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CVPageImageRendererTest {

	private byte[] pdfWithPages(int pages) throws Exception {
		try (var document = new PDDocument()) {
			for (int i = 0; i < pages; i++) {
				document.addPage(new PDPage());
			}
			var out = new ByteArrayOutputStream();
			document.save(out);
			return out.toByteArray();
		}
	}

	@Test
	void rendersPdfPagesAsPng() throws Exception {
		var result = CVPageImageRenderer.render("cv.pdf", "application/pdf", pdfWithPages(2));
		assertThat(result).hasSize(2);
		assertThat(result).allSatisfy(page -> {
			assertThat(page.mimeType()).isEqualTo("image/png");
			// PNG magic bytes
			assertThat(page.bytes()[1]).isEqualTo((byte) 'P');
			assertThat(page.bytes()[2]).isEqualTo((byte) 'N');
			assertThat(page.bytes()[3]).isEqualTo((byte) 'G');
		});
	}

	@Test
	void capsPdfRenderingAtMaxPages() throws Exception {
		var result = CVPageImageRenderer.render("cv.pdf", "application/pdf",
			pdfWithPages(CVPageImageRenderer.MAX_PDF_PAGES + 4));
		assertThat(result).hasSize(CVPageImageRenderer.MAX_PDF_PAGES);
	}

	@Test
	void docxWithoutPicturesYieldsNothing() throws Exception {
		byte[] docx;
		try (var document = new XWPFDocument()) {
			var out = new ByteArrayOutputStream();
			document.write(out);
			docx = out.toByteArray();
		}
		var result = CVPageImageRenderer.render("cv.docx",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);
		assertThat(result).isEmpty();
	}

	@Test
	void unknownTypesAndGarbageAreSafe() {
		assertThat(CVPageImageRenderer.render("cv.txt", "text/plain", new byte[] {1, 2, 3})).isEmpty();
		assertThat(CVPageImageRenderer.render("cv.pdf", "application/pdf", new byte[] {1, 2, 3})).isEmpty();
	}
}
