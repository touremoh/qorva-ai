package ai.qorva.core.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * In-memory {@link MultipartFile} over staged bytes — lets S3-staged uploads (async
 * candidate submissions) flow through the same ingest pipeline as live HTTP uploads.
 */
public class ByteArrayMultipartFile implements MultipartFile {

	private final byte[] bytes;
	private final String originalFilename;
	private final String contentType;

	public ByteArrayMultipartFile(byte[] bytes, String originalFilename, String contentType) {
		this.bytes = bytes;
		this.originalFilename = originalFilename;
		this.contentType = contentType;
	}

	@Override
	public String getName() {
		return "file";
	}

	@Override
	public String getOriginalFilename() {
		return originalFilename;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public boolean isEmpty() {
		return bytes == null || bytes.length == 0;
	}

	@Override
	public long getSize() {
		return bytes != null ? bytes.length : 0;
	}

	@Override
	public byte[] getBytes() {
		return bytes;
	}

	@Override
	public InputStream getInputStream() {
		return new ByteArrayInputStream(bytes);
	}

	@Override
	public void transferTo(File dest) throws IOException, IllegalStateException {
		try (var out = new FileOutputStream(dest)) {
			out.write(bytes);
		}
	}
}
