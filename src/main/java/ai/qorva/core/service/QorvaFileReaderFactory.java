package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaException;
import org.springframework.web.multipart.MultipartFile;

public class QorvaFileReaderFactory {

    public static QorvaFileReader getFileReader(MultipartFile file) throws QorvaException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new QorvaException("File name is null");
        }

        if (fileName.endsWith(".pdf")) {
            return QorvaFileReaders.PDF_READER;
        } else if (fileName.endsWith(".docx")) {
            return QorvaFileReaders.WORD_READER;
        } else {
            throw new QorvaException("Unsupported file type: " + fileName);
        }
    }
}
