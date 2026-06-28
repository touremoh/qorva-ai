package ai.qorva.core.service;

import ai.qorva.core.config.S3Properties;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

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
