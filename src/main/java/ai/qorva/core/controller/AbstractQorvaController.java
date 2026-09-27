package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.CrudOperation;
import ai.qorva.core.security.CrudPolicy;
import ai.qorva.core.security.LanguageContextHolder;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.QorvaApiAccessManager;
import ai.qorva.core.service.QorvaService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static ai.qorva.core.enums.QorvaErrorsEnum.RESOURCE_NOT_FOUND;

public abstract class AbstractQorvaController<D extends QorvaDTO> {

    protected final QorvaService<D> service;

    private QorvaApiAccessManager accessManager;

    protected AbstractQorvaController(QorvaService<D> service) {
        this.service = service;
    }

    @Autowired
    void setAccessManager(QorvaApiAccessManager accessManager) {
        this.accessManager = accessManager;
    }

    /** Which inherited operations this controller exposes, and the action each one requires. */
    protected abstract CrudPolicy crudPolicy();

    protected String currentTenantId() {
        return TenantContextHolder.getTenantId();
    }

    /**
     * Rejects an operation the policy does not declare (404, as if the route did not exist) or
     * whose action the caller does not hold (403, same body as a failed {@code @PreAuthorize}).
     */
    protected void authorize(CrudOperation operation) throws QorvaException {
        var policy = crudPolicy();
        if (!policy.isEnabled(operation)) {
            throw new QorvaException(
                RESOURCE_NOT_FOUND.getMessageKey(),
                RESOURCE_NOT_FOUND.getHttpStatus().value(),
                RESOURCE_NOT_FOUND.getHttpStatus());
        }
        var action = policy.requiredAction(operation);
        if (action.isPresent() && !currentUserHas(action.get())) {
            throw new AccessDeniedException("Missing " + action.get() + " for " + operation);
        }
    }

    protected boolean currentUserHas(String action) {
        return accessManager.hasPermission(SecurityContextHolder.getContext().getAuthentication(), action);
    }

    @GetMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> findOneById(@PathVariable String id) throws QorvaException {
        authorize(CrudOperation.GET_ONE);
        return BuildApiResponse.from(this.service.findOneById(id));
    }

    @PostMapping("/search")
    public ResponseEntity<QorvaRequestResponse> findOneByData(@RequestBody D requestData) throws QorvaException {
        authorize(CrudOperation.SEARCH);
        requestData.setTenantId(currentTenantId());
        return BuildApiResponse.from(this.service.findOneByCriteria(requestData));
    }

    @PostMapping
    public ResponseEntity<QorvaRequestResponse> createOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @RequestBody D data) throws QorvaException {
        authorize(CrudOperation.CREATE);
        LanguageContextHolder.setLanguage(language);
        // The store assigns ids: a client-chosen id must never reach the insert.
        data.setId(null);
        data.setTenantId(currentTenantId());
        return BuildApiResponse.from(this.service.createOne(data));
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<QorvaRequestResponse> findAll(@RequestParam Map<String, String> params) throws QorvaException {
        authorize(CrudOperation.LIST);
        var mutableParams = new java.util.HashMap<>(params);
        mutableParams.put("tenantId", currentTenantId());
        return BuildApiResponse.from(this.service.findAll(mutableParams));
    }

    @PostMapping("/ids")
    public ResponseEntity<QorvaRequestResponse> findManyByIds(@RequestBody List<String> ids) throws QorvaException {
        authorize(CrudOperation.FIND_BY_IDS);
        // Tenant isolation is enforced inside findAllByIds via TenantContextHolder
        return BuildApiResponse.from(this.service.findAllByIds(ids));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> updateOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @PathVariable String id,
            @RequestBody D data) throws QorvaException {
        authorize(CrudOperation.UPDATE);
        LanguageContextHolder.setLanguage(language);
        return BuildApiResponse.from(this.service.updateOne(id, data));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> patchOne(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @PathVariable String id,
            @RequestBody D data) throws QorvaException {
        authorize(CrudOperation.UPDATE);
        LanguageContextHolder.setLanguage(language);
        return BuildApiResponse.from(this.service.updateOne(id, data));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> deleteOneById(@PathVariable String id) throws QorvaException {
        authorize(CrudOperation.DELETE);
        this.service.deleteOneById(id, currentTenantId());
        return BuildApiResponse.from(true);
    }

    @PostMapping("/exists")
    public ResponseEntity<QorvaRequestResponse> existsByData(@RequestBody D data) throws QorvaException {
        authorize(CrudOperation.EXISTS);
        data.setTenantId(currentTenantId());
        return BuildApiResponse.from(this.service.existsByData(data));
    }
}
