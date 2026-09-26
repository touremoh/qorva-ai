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
import java.util.Calendar;
import java.time.Instant;

@Slf4j
public class QorvaFileReaders {
    public static final QorvaFileReader PDF_READER = new QorvaFileReader() {
        @Override
        public String read(MultipartFile file) throws QorvaException {
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
        }

        /** The PDF's own modification (else creation) date: how recent the resume is, when the file says so. */
        @Override
        public Instant readDocumentDate(MultipartFile file) {
            try (PDDocument pdfDocument = Loader.loadPDF(file.getBytes())) {
                var info = pdfDocument.getDocumentInformation();
                if (info == null) {
                    return null;
                }
                Calendar date = info.getModificationDate() != null ? info.getModificationDate() : info.getCreationDate();
                return date != null ? date.toInstant() : null;
            } catch (Exception e) {
                log.debug("Could not read PDF metadata date from {}: {}", file.getOriginalFilename(), e.getMessage());
                return null;
            }
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
