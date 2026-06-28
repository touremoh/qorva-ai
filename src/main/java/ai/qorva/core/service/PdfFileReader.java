package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaErrorCodes;
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
			throw new QorvaException(QorvaErrorCodes.FILE_EMPTY, file.getOriginalFilename());
		}

		try (PDDocument pdfDocument = Loader.loadPDF(file.getBytes())) {
			PDFTextStripper stripper = new PDFTextStripper();
			return stripper.getText(pdfDocument);
		} catch (IOException e) {
			log.error("Error reading file content: {}", file.getOriginalFilename(), e);
			throw new QorvaException(QorvaErrorCodes.FILE_READ_FAILED, e, file.getOriginalFilename());
		}
	}
}
