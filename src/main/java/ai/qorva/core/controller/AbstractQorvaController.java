package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.requests.QorvaRequestMapper;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.QorvaService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

public abstract class AbstractQorvaController<D extends QorvaDTO> {

    protected final QorvaService<D> service;
    protected final QorvaRequestMapper<D> requestMapper;

    protected AbstractQorvaController(QorvaService<D> service, QorvaRequestMapper<D> requestMapper) {
        this.service = service;
        this.requestMapper = requestMapper;
    }

    /** Returns the tenant ID of the currently authenticated request from the thread-local context. */
    protected String currentTenantId() {
        return TenantContextHolder.getTenantId();
    }

    @GetMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> findOneById(@PathVariable String id) throws QorvaException {
        return BuildApiResponse.from(this.service.findOneById(id));
    }

    @PostMapping("/search")
    public ResponseEntity<QorvaRequestResponse> findOneByData(@RequestBody D requestData) throws QorvaException {
        requestData.setTenantId(currentTenantId());
        return BuildApiResponse.from(this.service.findOneByData(requestData));
    }

    @PostMapping
    public ResponseEntity<QorvaRequestResponse> createOne(@RequestBody D data) throws QorvaException {
        data.setTenantId(currentTenantId());
        return BuildApiResponse.from(this.service.createOne(data));
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<QorvaRequestResponse> findAll(@RequestParam Map<String, String> params) throws QorvaException {
        var data = this.requestMapper.toDto(params);
        var pageNumber = Integer.parseInt(params.getOrDefault("pageNumber", "0"));
        var pageSize = Integer.parseInt(params.getOrDefault("pageSize", "25"));
        data.setTenantId(currentTenantId());
        return BuildApiResponse.from(this.service.findAll(data, pageNumber, pageSize));
    }

    @PostMapping("/ids")
    public ResponseEntity<QorvaRequestResponse> findManyByIds(@RequestBody List<String> ids) throws QorvaException {
        // Tenant isolation is enforced inside findAllByIds via TenantContextHolder
        return BuildApiResponse.from(this.service.findAllByIds(ids));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> updateOne(@PathVariable String id, @RequestBody D data) throws QorvaException {
        // preProcessUpdateOne in the service will verify ownership and set tenantId from the existing entity
        return BuildApiResponse.from(this.service.updateOne(id, data));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> patchOne(@PathVariable String id, @RequestBody D data) throws QorvaException {
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
