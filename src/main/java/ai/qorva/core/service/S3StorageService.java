package ai.qorva.core.service;

import ai.qorva.core.config.S3Properties;
import ai.qorva.core.dto.common.AttachmentInfo;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public String uploadTenantLogo(String tenantId, MultipartFile file) throws QorvaException {
        var extension = resolveExtension(file);
        var key = "tenants/" + tenantId + "/logos/logo." + extension;

        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException e) {
            log.error("S3 - Failed to read logo file for tenant {}: {}", tenantId, e.getMessage());
            throw new QorvaException(QorvaErrorCodes.COMPANY_LOGO_UPLOAD_FAILED, e);
        } catch (Exception e) {
            log.error("S3 - Failed to upload logo for tenant {}: {}", tenantId, e.getMessage());
            throw new QorvaException(QorvaErrorCodes.COMPANY_LOGO_UPLOAD_FAILED, e);
        }

        var url = "https://" + s3Properties.getBucketName()
            + ".s3." + s3Properties.getRegion()
            + ".amazonaws.com/" + key;

        log.debug("S3 - Logo uploaded for tenant {}: {}", tenantId, url);
        return url;
    }

    public record LogoData(byte[] bytes, String contentType) {}

    public LogoData fetchTenantLogo(String logoUrl) throws QorvaException {
        var key = extractKey(logoUrl);
        try {
            var response = s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .build()
            );
            var contentType = response.response().contentType();
            return new LogoData(response.asByteArray(), contentType != null ? contentType : "image/jpeg");
        } catch (NoSuchKeyException e) {
            throw new QorvaException(QorvaErrorCodes.COMPANY_LOGO_NOT_FOUND);
        } catch (Exception e) {
            log.error("S3 - Failed to fetch logo key {}: {}", key, e.getMessage());
            throw new QorvaException(QorvaErrorCodes.COMPANY_LOGO_FETCH_FAILED, e);
        }
    }

    /**
     * Stores the original CV file under {@code tenants/{tenantId}/cvs/{uuid}.{ext}}.
     */
    public AttachmentInfo uploadCvDocument(String tenantId, MultipartFile file) throws QorvaException {
        var key = "tenants/" + tenantId + "/cvs/" + UUID.randomUUID() + "." + resolveDocExtension(file);

        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException | RuntimeException e) {
            log.error("S3 - Failed to upload CV document for tenant {}: {}", tenantId, e.getMessage());
            throw new QorvaException(QorvaErrorCodes.CV_ATTACHMENT_UPLOAD_FAILED, e, file.getOriginalFilename());
        }

        log.debug("S3 - CV document uploaded for tenant {}: {}", tenantId, key);
        return new AttachmentInfo(key, file.getOriginalFilename(), file.getContentType(), file.getSize());
    }

    /**
     * Stages a candidate-submitted resume for asynchronous processing under
     * {@code candidate-submissions/{tenantId}/{requestId}}. Deleted by the worker on
     * terminal states; an S3 lifecycle rule on the prefix is the cleanup backstop.
     */
    public String uploadCandidateSubmission(String tenantId, String requestId, MultipartFile file) throws QorvaException {
        var key = "candidate-submissions/" + tenantId + "/" + requestId;
        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException | RuntimeException e) {
            log.error("S3 - Failed to stage candidate submission for request {}: {}", requestId, e.getMessage());
            throw new QorvaException(QorvaErrorCodes.CV_ATTACHMENT_UPLOAD_FAILED, e, file.getOriginalFilename());
        }
        log.debug("S3 - Candidate submission staged: {}", key);
        return key;
    }

    public byte[] fetchObjectBytes(String key) throws QorvaException {
        try {
            return s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .build()
            ).asByteArray();
        } catch (Exception e) {
            log.error("S3 - Failed to fetch object {}: {}", key, e.getMessage());
            throw new QorvaException(QorvaErrorCodes.CV_ATTACHMENT_UPLOAD_FAILED, e, key);
        }
    }

    /** Best-effort delete of a single object; never throws. */
    public void deleteObject(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(s3Properties.getBucketName())
                .key(key)
                .build());
            log.debug("S3 - Deleted object {}", key);
        } catch (Exception e) {
            log.warn("S3 - Failed to delete object {}: {}", key, e.getMessage());
        }
    }

    /** Best-effort removal of every CV document belonging to a tenant; never throws. */
    public void deleteCvDocumentsForTenant(String tenantId) {
        var prefix = "tenants/" + tenantId + "/cvs/";
        try {
            String continuationToken = null;
            long deleted = 0;
            do {
                var listResponse = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(s3Properties.getBucketName())
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build());

                var identifiers = listResponse.contents().stream()
                    .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                    .toList();

                if (!identifiers.isEmpty()) {
                    s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(s3Properties.getBucketName())
                        .delete(Delete.builder().objects(identifiers).build())
                        .build());
                    deleted += identifiers.size();
                }
                continuationToken = Boolean.TRUE.equals(listResponse.isTruncated())
                    ? listResponse.nextContinuationToken()
                    : null;
            } while (continuationToken != null);
            log.info("S3 - Purged {} CV documents for tenant {}", deleted, tenantId);
        } catch (Exception e) {
            log.warn("S3 - Failed to purge CV documents for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    private String resolveDocExtension(MultipartFile file) {
        var originalFilename = file.getOriginalFilename();
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        var contentType = file.getContentType();
        if (contentType != null) {
            return switch (contentType) {
                case "application/pdf" -> "pdf";
                case "application/msword" -> "doc";
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
                default -> "bin";
            };
        }
        return "bin";
    }

    private String extractKey(String logoUrl) {
        var marker = ".amazonaws.com/";
        var idx = logoUrl.indexOf(marker);
        return idx >= 0 ? logoUrl.substring(idx + marker.length()) : logoUrl;
    }

    private String resolveExtension(MultipartFile file) {
        var originalFilename = file.getOriginalFilename();
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }
        var contentType = file.getContentType();
        if (contentType != null) {
            return switch (contentType) {
                case "image/png" -> "png";
                case "image/svg+xml" -> "svg";
                case "image/webp" -> "webp";
                default -> "jpg";
            };
        }
        return "jpg";
    }
}
