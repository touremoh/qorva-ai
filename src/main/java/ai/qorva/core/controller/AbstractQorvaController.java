package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.LanguageContextHolder;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.QorvaService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

public abstract class AbstractQorvaController<D extends QorvaDTO> {

    protected final QorvaService<D> service;

    protected AbstractQorvaController(QorvaService<D> service) {
        this.service = service;
    }

    protected String currentTenantId() {
        return TenantContextHolder.getTenantId();
    }

    protected String currentLanguage() {
        return LanguageContextHolder.getLanguage();
    }

    @GetMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> findOneById(@PathVariable String id) throws QorvaException {
        return BuildApiResponse.from(this.service.findOneById(id));
    }

    @PostMapping("/search")
    public ResponseEntity<QorvaRequestResponse> findOneByData(@RequestBody D requestData) throws QorvaException {
        requestData.setTenantId(currentTenantId());
        return BuildApiResponse.from(this.service.findOneByCriteria(requestData));
    }

    @PostMapping
    public ResponseEntity<QorvaRequestResponse> createOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @RequestBody D data) throws QorvaException {
        LanguageContextHolder.setLanguage(language);
        data.setTenantId(currentTenantId());
        return BuildApiResponse.from(this.service.createOne(data));
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<QorvaRequestResponse> findAll(@RequestParam Map<String, String> params) throws QorvaException {
        var mutableParams = new java.util.HashMap<>(params);
        mutableParams.put("tenantId", currentTenantId());
        return BuildApiResponse.from(this.service.findAll(mutableParams));
    }

    @PostMapping("/ids")
    public ResponseEntity<QorvaRequestResponse> findManyByIds(@RequestBody List<String> ids) throws QorvaException {
        // Tenant isolation is enforced inside findAllByIds via TenantContextHolder
        return BuildApiResponse.from(this.service.findAllByIds(ids));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> updateOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @PathVariable String id,
            @RequestBody D data) throws QorvaException {
        LanguageContextHolder.setLanguage(language);
        return BuildApiResponse.from(this.service.updateOne(id, data));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> patchOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @PathVariable String id,
            @RequestBody D data) throws QorvaException {
        LanguageContextHolder.setLanguage(language);
        return BuildApiResponse.from(this.service.updateOne(id, data));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> deleteOneById(@PathVariable String id) throws QorvaException {
        this.service.deleteOneById(id, currentTenantId());
        return BuildApiResponse.from(true);
    }

    @PostMapping("/exists")
    public ResponseEntity<QorvaRequestResponse> existsByData(@RequestBody D data) throws QorvaException {
        data.setTenantId(currentTenantId());
        return BuildApiResponse.from(this.service.existsByData(data));
    }
}
