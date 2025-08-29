package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaException;
import org.springframework.web.multipart.MultipartFile;

public class QorvaFileReaderContext {
    private final QorvaFileReader fileReader;

    public QorvaFileReaderContext(QorvaFileReader fileReader) {
        this.fileReader = fileReader;
    }

    public String readFile(MultipartFile file) throws QorvaException {
        return fileReader.read(file);
    }
}
