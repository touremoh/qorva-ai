package ai.qorva.core.controller;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.utils.BuildApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
    public ResponseEntity<List<CVDTO>> uploadFiles(@RequestParam("files") List<MultipartFile> files) throws QorvaException {
        log.info("Received {} files", files.size());
        return ResponseEntity.ok(((CVService) service).upload(files, currentTenantId()));
    }

    @GetMapping("/search")
    public ResponseEntity<QorvaRequestResponse> searchAll(
        @RequestParam("searchTerms") String searchTerms,
        @RequestParam("pageSize") int pageSize,
        @RequestParam("pageNumber") int pageNumber) throws QorvaException {
        return BuildApiResponse.from(((CVService) this.service).searchAll(currentTenantId(), searchTerms, pageSize, pageNumber));
    }

    @GetMapping("/tags")
    public ResponseEntity<QorvaRequestResponse> findAllTagsByTenantId() {
        return BuildApiResponse.from(((CVService) this.service).findAllTagsByTenantId(currentTenantId()));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getCVInPdfFormat(@PathVariable("id") String cvId, @RequestParam(defaultValue = "en") String lang) throws QorvaException {
        byte[] pdfBytes = ((CVService) this.service).generateCVInPdfFormat(cvId, lang);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cv-" + cvId + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdfBytes.length)
            .body(pdfBytes);
    }
}
