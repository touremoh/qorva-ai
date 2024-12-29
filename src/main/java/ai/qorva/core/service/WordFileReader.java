package ai.qorva.core.service;

import ai.qorva.core.exception.QorvaException;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@NoArgsConstructor
public class WordFileReader implements QorvaFileReader {
	@Override
	public String read(MultipartFile file) throws QorvaException {
		return "";
	}
}
