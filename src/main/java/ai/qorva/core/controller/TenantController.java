package ai.qorva.core.controller;

import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.TenantProfileUpdateDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.S3StorageService;
import ai.qorva.core.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/tenants")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class TenantController extends AbstractQorvaController<TenantDTO> {

    private final TenantService tenantService;
    private final S3StorageService s3StorageService;

    protected TenantController(TenantService tenantService, S3StorageService s3StorageService) {
        super(tenantService);
		this.tenantService = tenantService;
		this.s3StorageService = s3StorageService;
    }


    @PatchMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TenantDTO> updateProfile(
        @RequestPart("profile") TenantProfileUpdateDTO profile,
        @RequestPart(value = "logo", required = false) MultipartFile logo
    ) throws QorvaException {
        var tenantId = TenantContextHolder.getTenantId();

        var dto = new TenantDTO();
        dto.setTenantName(profile.getTenantName());
        dto.setCompanyAddress(profile.getCompanyAddress());
        dto.setPhoneNumber(profile.getPhoneNumber());
        dto.setContactEmail(profile.getContactEmail());
        dto.setWebsiteUrl(profile.getWebsiteUrl());

        if (logo != null && !logo.isEmpty()) {
            var logoUrl = s3StorageService.uploadTenantLogo(tenantId, logo);
            dto.setCompanyLogoUrl(logoUrl);
        }

        var updated = tenantService.updateOne(tenantId, dto);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> getLogo() throws QorvaException {
        var tenantId = TenantContextHolder.getTenantId();
        var tenant = tenantService.findOneById(tenantId);

        if (tenant == null || !StringUtils.hasText(tenant.getCompanyLogoUrl())) {
            return ResponseEntity.notFound().build();
        }

        var logo = s3StorageService.fetchTenantLogo(tenant.getCompanyLogoUrl());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, logo.contentType())
            .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
            .body(logo.bytes());
    }
}
