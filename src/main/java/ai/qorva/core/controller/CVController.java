package ai.qorva.core.controller;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.requests.CVRequestMapper;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.QorvaUserDetailsService;
import ai.qorva.core.utils.BuildApiResponse;
import ai.qorva.core.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    public CVController(CVService service, CVRequestMapper requestMapper, QorvaUserDetailsService userService, JwtConfig jwtConfig) {
        super(service, requestMapper, userService, jwtConfig);
	}

    @PostMapping(value = "/upload")
    public ResponseEntity<List<CVDTO>> uploadFiles(@RequestHeader("Authorization") String authorizationHeader, @RequestParam("files") List<MultipartFile> files) throws QorvaException {
        log.info("Received {} files", files.size());
        return ResponseEntity.ok(((CVService) service).upload(files, JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey())));
    }

    @GetMapping("/search")
    public ResponseEntity<QorvaRequestResponse> searchAll(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestParam("searchTerms") String searchTerms,
        @RequestParam("pageSize") int pageSize,
        @RequestParam("pageNumber") int pageNumber) throws QorvaException {
        var tenantId = JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey());
        return BuildApiResponse.from(((CVService)this.service).searchAll(tenantId, searchTerms, pageSize, pageNumber));
    }

    @GetMapping("/tags")
    public ResponseEntity<QorvaRequestResponse> findAllTagsByTenantId(@RequestHeader("Authorization") String authorizationHeader) {
        return BuildApiResponse.from(((CVService)this.service).findAllTagsByTenantId(JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey())));
    }
}
