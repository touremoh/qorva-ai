package ai.qorva.core.controller;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CVDuplicatesData;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.UploadResult;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.utils.BuildApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import ai.qorva.core.security.LanguageContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/*
 * Write endpoints are authority-gated so demo users (who lack ADD_CV/MODIFY_CV/DELETE_CV) cannot mutate data.
 */

@Slf4j
@RestController
@RequestMapping("/cvs")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class CVController extends AbstractQorvaController<CVDTO> {

    @Autowired
    public CVController(CVService service) {
        super(service);
	}

    @PostMapping(value = "/upload")
    @PreAuthorize("@accessManager.hasPermission(authentication,'ADD_CV') and @accessManager.hasNotExceededScreeningLimit()")
    public ResponseEntity<List<UploadResult>> uploadFiles(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @RequestParam("files") List<MultipartFile> files) throws QorvaException {
        LanguageContextHolder.setLanguage(language);
        log.info("Received {} files", files.size());
        return ResponseEntity.ok(((CVService) service).upload(files, currentTenantId()));
    }

    /** Resolves an upload-time duplicate: keeps the new CV (merging tags) and deletes the old copy. */
    @PostMapping("/{newCvId}/replace/{oldCvId}")
    @PreAuthorize("@accessManager.hasPermission(authentication,'DELETE_CV')")
    public ResponseEntity<CVDTO> replaceDuplicate(
            @PathVariable String newCvId,
            @PathVariable String oldCvId) throws QorvaException {
        return ResponseEntity.ok(((CVService) service).replaceDuplicate(newCvId, oldCvId, currentTenantId()));
    }

    @Override
    @PostMapping
    @PreAuthorize("@accessManager.hasPermission(authentication,'ADD_CV')")
    public ResponseEntity<QorvaRequestResponse> createOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @RequestBody CVDTO data) throws QorvaException {
        return super.createOne(language, data);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("@accessManager.hasPermission(authentication,'MODIFY_CV')")
    public ResponseEntity<QorvaRequestResponse> updateOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @PathVariable String id,
            @RequestBody CVDTO data) throws QorvaException {
        return super.updateOne(language, id, data);
    }

    @Override
    @PatchMapping("/{id}")
    @PreAuthorize("@accessManager.hasPermission(authentication,'MODIFY_CV')")
    public ResponseEntity<QorvaRequestResponse> patchOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @PathVariable String id,
            @RequestBody CVDTO data) throws QorvaException {
        return super.patchOne(language, id, data);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("@accessManager.hasPermission(authentication,'DELETE_CV')")
    public ResponseEntity<QorvaRequestResponse> deleteOneById(@PathVariable String id) throws QorvaException {
        return super.deleteOneById(id);
    }

    @GetMapping("/search")
    public ResponseEntity<QorvaRequestResponse> searchAll(
        @RequestParam("searchTerms") String searchTerms,
        @RequestParam("pageSize") int pageSize,
        @RequestParam("pageNumber") int pageNumber) throws QorvaException {
        return BuildApiResponse.from(((CVService) this.service).searchAll(currentTenantId(), searchTerms, pageSize, pageNumber));
    }

    @GetMapping("/duplicates")
    public ResponseEntity<CVDuplicatesData.DuplicatesPage> findDuplicates(
        @RequestParam(defaultValue = "0") int pageNumber,
        @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(((CVService) service).findDuplicates(currentTenantId(), pageNumber, pageSize));
    }

    @GetMapping("/tags")
    public ResponseEntity<QorvaRequestResponse> findAllTagsByTenantId() {
        return BuildApiResponse.from(((CVService) this.service).findAllTagsByTenantId(currentTenantId()));
    }

}
