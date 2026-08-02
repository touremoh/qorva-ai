package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;

@FunctionalInterface
public interface QorvaFileReader {
	/**
	 * Read file content
	 * @param file file to read
	 * @return the  content of the file in string format
	 * @throws IOException if something went wrong
	 */
	String read(MultipartFile file) throws QorvaException;

	/**
	 * Best-effort extraction of the document's own creation/modification date from its metadata.
	 * Used as freshness evidence (contentDate); {@code null} when the format carries none or reading fails.
	 */
	default Instant readDocumentDate(MultipartFile file) {
		return null;
	}
}
