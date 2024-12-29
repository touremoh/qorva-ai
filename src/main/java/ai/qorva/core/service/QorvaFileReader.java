package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@FunctionalInterface
public interface QorvaFileReader {
	/**
	 * Read file content
	 * @param file file to read
	 * @return the  content of the file in string format
	 * @throws IOException if something went wrong
	 */
	String read(MultipartFile file) throws QorvaException;
}
