package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.request.FindManyRequestCriteria;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.QorvaService;
import ai.qorva.core.utils.BuildApiResponse;
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
        return BuildApiResponse.from(service.findOneById(id));
    }

    @PostMapping("/search")
    public ResponseEntity<QorvaRequestResponse> findOneByData(@RequestBody D requestData) throws QorvaException {
        return BuildApiResponse.from(service.findOneByData(requestData));
    }

    @PostMapping
    public ResponseEntity<QorvaRequestResponse> createOne(@RequestBody D data) throws QorvaException {
        return BuildApiResponse.from(service.createOne(data));
    }

    @GetMapping
    public ResponseEntity<QorvaRequestResponse> findMany(FindManyRequestCriteria requestCriteria) throws QorvaException {
        return BuildApiResponse.from(service.findManyByText(requestCriteria));
    }

    @GetMapping("/search")
    public ResponseEntity<QorvaRequestResponse> findManyByText(FindManyRequestCriteria requestCriteria) throws QorvaException {
        return BuildApiResponse.from(service.findManyByText(requestCriteria));
    }

    @PostMapping("/ids")
    public ResponseEntity<QorvaRequestResponse> findManyByIds(@RequestBody List<String> ids) throws QorvaException {
        return BuildApiResponse.from(service.findManyByIds(ids));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> updateOne(@PathVariable String id, @RequestBody D data) throws QorvaException {
        return BuildApiResponse.from(service.updateOne(id, data));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> patchOne(@PathVariable String id, @RequestBody D data) throws QorvaException {
        return BuildApiResponse.from(service.updateOne(id, data));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> deleteOneById(@PathVariable String id) throws QorvaException {
        service.deleteOneById(id);
        return BuildApiResponse.from(true);
    }

    @PostMapping("/exists")
    public ResponseEntity<QorvaRequestResponse> existsByData(@RequestBody D data) throws QorvaException {
        return BuildApiResponse.from(service.existsByData(data));
    }
}
