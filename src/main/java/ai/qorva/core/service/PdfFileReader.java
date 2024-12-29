package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaException;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@NoArgsConstructor
public class PdfFileReader implements QorvaFileReader {

	@Override
	public String read(MultipartFile file) throws QorvaException {
		if (file.isEmpty()) {
			throw new QorvaException("File is empty: " + file.getOriginalFilename());
		}

		try (PDDocument pdfDocument = Loader.loadPDF(file.getBytes())) {
			PDFTextStripper stripper = new PDFTextStripper();
			return stripper.getText(pdfDocument);
		} catch (IOException e) {
			throw new QorvaException("Error reading file content: " + file.getOriginalFilename(), e);
		}
	}
}
