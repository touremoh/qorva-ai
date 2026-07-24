package ai.qorva.core.service;

import ai.qorva.core.config.S3Properties;
import ai.qorva.core.exception.QorvaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

	@Mock
	private S3Client s3Client;

	private S3StorageService service;

	@BeforeEach
	void setUp() {
		var properties = new S3Properties();
		properties.setBucketName("qorva-test-bucket");
		properties.setRegion("eu-west-1");
		service = new S3StorageService(s3Client, properties);
	}

	@Test
	void uploadCvDocument_buildsTenantScopedKeyAndMetadata() throws QorvaException {
		var file = new MockMultipartFile("files", "John Doe CV.pdf", "application/pdf", "pdf-bytes".getBytes());

		var result = service.uploadCvDocument("tenant123", file);

		var captor = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

		assertThat(captor.getValue().bucket()).isEqualTo("qorva-test-bucket");
		assertThat(captor.getValue().key()).startsWith("tenants/tenant123/cvs/").endsWith(".pdf");
		assertThat(result.getS3Key()).isEqualTo(captor.getValue().key());
		assertThat(result.getFileName()).isEqualTo("John Doe CV.pdf");
		assertThat(result.getContentType()).isEqualTo("application/pdf");
		assertThat(result.getSizeBytes()).isEqualTo(9L);
	}

	@Test
	void uploadCvDocument_extensionFallsBackToContentType() throws QorvaException {
		var file = new MockMultipartFile("files", "resume", "application/pdf", "x".getBytes());

		service.uploadCvDocument("tenant123", file);

		var captor = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
		assertThat(captor.getValue().key()).endsWith(".pdf");
	}

	@Test
	void uploadCvDocument_s3Failure_throwsQorvaException() {
		var file = new MockMultipartFile("files", "cv.pdf", "application/pdf", "x".getBytes());
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenThrow(S3Exception.builder().message("boom").build());

		assertThatThrownBy(() -> service.uploadCvDocument("tenant123", file))
			.isInstanceOf(QorvaException.class);
	}

	@Test
	void deleteObject_neverThrows() {
		when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
			.thenThrow(S3Exception.builder().message("boom").build());

		assertThatCode(() -> service.deleteObject("tenants/t/cvs/x.pdf")).doesNotThrowAnyException();
		assertThatCode(() -> service.deleteObject(null)).doesNotThrowAnyException();
	}
}
