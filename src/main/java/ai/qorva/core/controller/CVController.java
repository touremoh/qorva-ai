package ai.qorva.core.controller;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVService;
import ai.qorva.core.utils.BuildApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/cv")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class CVController extends AbstractQorvaController<CVDTO> {

    @Autowired
    public CVController(CVService service) {
        super(service);
    }

    @PostMapping(value = "/upload")
    public ResponseEntity<List<CVDTO>> uploadFiles(@RequestParam("files") List<MultipartFile> files, @RequestHeader String tenantId) throws QorvaException {
        log.info("Received {} files", files.size());
        return ResponseEntity.ok(((CVService) service).upload(files, tenantId));
    }

    @GetMapping("/search")
    public ResponseEntity<QorvaRequestResponse> searchAll(
        @RequestHeader String tenantId,
        @RequestParam String searchTerms,
        @RequestParam int pageSize,
        @RequestParam int pageNumber) throws QorvaException {
        return BuildApiResponse.from(((CVService)this.service).searchAll(searchTerms, tenantId, pageSize, pageNumber));
    }
}
