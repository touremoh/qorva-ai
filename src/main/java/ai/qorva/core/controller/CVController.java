package ai.qorva.core.controller;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.CVDuplicatesData;
import ai.qorva.core.dto.CVFilterOptionsData;
import ai.qorva.core.dto.LibraryClearData;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.UploadResult;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.LibraryClearService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import ai.qorva.core.utils.BuildApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import ai.qorva.core.security.CrudPolicy;
import ai.qorva.core.security.LanguageContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

import static ai.qorva.core.security.CrudOperation.*;

@Slf4j
@RestController
@RequestMapping("/cvs")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class CVController extends AbstractQorvaController<CVDTO> {

    private final LibraryClearService libraryClearService;

    @Autowired
    public CVController(CVService service, LibraryClearService libraryClearService) {
        super(service);
        this.libraryClearService = libraryClearService;
	}

    @GetMapping("/clear-library/preflight")
    @PreAuthorize("@accessManager.hasPermission(authentication,'DELETE_CV')")
    public ResponseEntity<LibraryClearData.Preflight> clearLibraryPreflight() {
        return ResponseEntity.ok(libraryClearService.preflight(currentTenantId()));
    }

    /** Permanently wipes the resume library and everything derived from it. Jobs and usage survive. */
    @PostMapping("/clear-library")
    @PreAuthorize("@accessManager.hasPermission(authentication,'DELETE_CV')")
    public ResponseEntity<LibraryClearData.Result> clearLibrary(
        @AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
        return ResponseEntity.ok(libraryClearService.clear(
            currentTenantId(),
            userDetails != null ? userDetails.getUsername() : null));
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

    /*
     * Reads need VIEW_CV and writes their own action, so demo users (who lack ADD_CV/MODIFY_CV/DELETE_CV)
     * cannot mutate data. CVs are created through /upload, never through the generic POST.
     */
    @Override
    protected CrudPolicy crudPolicy() {
        return CrudPolicy.builder()
            .allow("VIEW_CV", GET_ONE, LIST, SEARCH, FIND_BY_IDS, EXISTS)
            .allow("ADD_CV", CREATE)
            .allow("MODIFY_CV", UPDATE)
            .allow("DELETE_CV", DELETE)
            .build();
    }

    /** Free-text lookup used by the resume-chat candidate picker and Talent Intelligence @mentions. */
    @GetMapping("/search")
    @PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_CV')")
    public ResponseEntity<QorvaRequestResponse> searchAll(
        @RequestParam("searchTerms") String searchTerms,
        @RequestParam("pageSize") int pageSize,
        @RequestParam("pageNumber") int pageNumber) throws QorvaException {
        return BuildApiResponse.from(((CVService) this.service).searchAll(currentTenantId(), searchTerms, pageSize, pageNumber));
    }

    /** Distinct values + counts for the list filter rail; mirrors the archived toggle of GET /cvs. */
    @GetMapping("/filter-options")
    @PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_CV')")
    public ResponseEntity<CVFilterOptionsData> filterOptions(
        @RequestParam(defaultValue = "false") boolean archived) {
        return ResponseEntity.ok(((CVService) service).filterOptions(currentTenantId(), archived));
    }

    @GetMapping("/duplicates")
    @PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_CV')")
    public ResponseEntity<CVDuplicatesData.DuplicatesPage> findDuplicates(
        @RequestParam(defaultValue = "0") int pageNumber,
        @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(((CVService) service).findDuplicates(currentTenantId(), pageNumber, pageSize));
    }

    @GetMapping("/tags")
    @PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_CV')")
    public ResponseEntity<QorvaRequestResponse> findAllTagsByTenantId() {
        return BuildApiResponse.from(((CVService) this.service).findAllTagsByTenantId(currentTenantId()));
    }

}
