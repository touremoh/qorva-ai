package ai.qorva.core.controller;

import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.JobPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * Write endpoints are authority-gated so demo users (who lack ADD_JOB/MODIFY_JOB/DELETE_JOB) cannot mutate data.
 */
@RestController
@RequestMapping("/jobs")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class JobPostController extends AbstractQorvaController<JobPostDTO> {

    @Autowired
    public JobPostController(JobPostService service) {
        super(service);
    }

    @Override
    @PostMapping
    @PreAuthorize("@accessManager.hasPermission(authentication,'ADD_JOB')")
    public ResponseEntity<QorvaRequestResponse> createOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @RequestBody JobPostDTO data) throws QorvaException {
        return super.createOne(language, data);
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("@accessManager.hasPermission(authentication,'MODIFY_JOB')")
    public ResponseEntity<QorvaRequestResponse> updateOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @PathVariable String id,
            @RequestBody JobPostDTO data) throws QorvaException {
        return super.updateOne(language, id, data);
    }

    @Override
    @PatchMapping("/{id}")
    @PreAuthorize("@accessManager.hasPermission(authentication,'MODIFY_JOB')")
    public ResponseEntity<QorvaRequestResponse> patchOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @PathVariable String id,
            @RequestBody JobPostDTO data) throws QorvaException {
        return super.patchOne(language, id, data);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("@accessManager.hasPermission(authentication,'DELETE_JOB')")
    public ResponseEntity<QorvaRequestResponse> deleteOneById(@PathVariable String id) throws QorvaException {
        return super.deleteOneById(id);
    }
}
