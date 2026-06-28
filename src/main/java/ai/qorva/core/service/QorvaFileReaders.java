package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
public class QorvaFileReaders {
    public static final QorvaFileReader PDF_READER = (MultipartFile file) -> {
        if (file.isEmpty()) {
            throw new QorvaException(QorvaErrorCodes.FILE_EMPTY, file.getOriginalFilename());
        }
        try (PDDocument pdfDocument = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(pdfDocument);
        } catch (IOException e) {
            log.error("Error reading PDF file: {}", file.getOriginalFilename(), e);
            throw new QorvaException(QorvaErrorCodes.FILE_PDF_READ_FAILED, e, file.getOriginalFilename());
        }
    };

    public static final QorvaFileReader WORD_READER = (MultipartFile file) -> {
        if (file.isEmpty()) {
            log.debug("File is empty: {}", file.getOriginalFilename());
            throw new QorvaException(QorvaErrorCodes.FILE_EMPTY, file.getOriginalFilename());
        }

        try (var document = new XWPFDocument(file.getInputStream())) {
            var docExtractor = new XWPFWordExtractor(document);
            return docExtractor.getText();
        } catch (IOException e) {
            log.error("Error reading Word file: {}", file.getOriginalFilename(), e);
            throw new QorvaException(QorvaErrorCodes.FILE_WORD_READ_FAILED, e, file.getOriginalFilename());
        }
    };
}
