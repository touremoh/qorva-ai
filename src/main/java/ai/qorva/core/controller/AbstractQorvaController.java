package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.QorvaService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public abstract class AbstractQorvaController<D extends QorvaDTO> {

    protected final QorvaService<D> service;

    protected AbstractQorvaController(QorvaService<D> service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> findOneById(@PathVariable String id) throws QorvaException {
        return BuildApiResponse.from(this.service.findOneById(id));
    }

    @PostMapping("/search")
    public ResponseEntity<QorvaRequestResponse> findOneByData(@RequestBody D requestData) throws QorvaException {
        return BuildApiResponse.from(this.service.findOneByData(requestData));
    }

    @PostMapping
    public ResponseEntity<QorvaRequestResponse> createOne(@RequestBody D data) throws QorvaException {
        return BuildApiResponse.from(this.service.createOne(data));
    }

    @GetMapping
    public ResponseEntity<QorvaRequestResponse> findAll(
        @RequestBody D data,
        @RequestParam(name = "pageSize", defaultValue = "50") int pageSize,
        @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber
    ) throws QorvaException {
        return BuildApiResponse.from(this.service.findAll(data, pageNumber, pageSize));
    }

    @PostMapping("/ids")
    public ResponseEntity<QorvaRequestResponse> findManyByIds(@RequestBody List<String> ids) throws QorvaException {
        return BuildApiResponse.from(this.service.findAllByIds(ids));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> updateOne(@PathVariable String id, @RequestBody D data) throws QorvaException {
        return BuildApiResponse.from(this.service.updateOne(id, data));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> patchOne(@PathVariable String id, @RequestBody D data) throws QorvaException {
        return BuildApiResponse.from(this.service.updateOne(id, data));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> deleteOneById(@PathVariable String id, @RequestHeader String tenantId) throws QorvaException {
        service.deleteOneById(id, tenantId);
        return BuildApiResponse.from(true);
    }

    @PostMapping("/exists")
    public ResponseEntity<QorvaRequestResponse> existsByData(@RequestBody D data) throws QorvaException {
        return BuildApiResponse.from(this.service.existsByData(data));
    }
}
