package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public class QorvaFileReaders {
    public static final QorvaFileReader PDF_READER = (MultipartFile file) -> {
        if (file.isEmpty()) {
            throw new QorvaException("File is empty: " + file.getOriginalFilename());
        }
        try (PDDocument pdfDocument = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(pdfDocument);
        } catch (IOException e) {
            throw new QorvaException("Error reading PDF file: " + file.getOriginalFilename(), e);
        }
    };

    public static final QorvaFileReader WORD_READER = (MultipartFile file) -> {
        if (file.isEmpty()) {
            throw new QorvaException("File is empty: " + file.getOriginalFilename());
        }

        try (var document = new XWPFDocument(file.getInputStream())) {

            // Create a word document extractor
            var docExtractor = new XWPFWordExtractor(document);

            // Extract document content
            return docExtractor.getText();
        } catch (IOException e) {
            throw new QorvaException("Error reading Word file: " + file.getOriginalFilename(), e);
        }
    };
}
