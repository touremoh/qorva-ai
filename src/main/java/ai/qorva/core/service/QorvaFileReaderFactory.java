package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import org.springframework.web.multipart.MultipartFile;

public class QorvaFileReaderFactory {

    public static QorvaFileReader getFileReader(MultipartFile file) throws QorvaException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new QorvaException(QorvaErrorCodes.FILE_NAME_NULL);
        }

        if (fileName.endsWith(".pdf")) {
            return QorvaFileReaders.PDF_READER;
        } else if (fileName.endsWith(".docx")) {
            return QorvaFileReaders.WORD_READER;
        } else {
            throw new QorvaException(QorvaErrorCodes.FILE_UNSUPPORTED_TYPE, fileName);
        }
    }
}
