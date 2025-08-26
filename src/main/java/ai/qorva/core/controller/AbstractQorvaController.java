package ai.qorva.core.controller;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dto.QorvaDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.requests.QorvaRequestMapper;
import ai.qorva.core.service.QorvaService;
import ai.qorva.core.service.QorvaUserDetailsService;
import ai.qorva.core.utils.BuildApiResponse;
import ai.qorva.core.utils.JwtUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

public abstract class AbstractQorvaController<D extends QorvaDTO> {

    protected final QorvaService<D> service;
    protected final QorvaRequestMapper<D> requestMapper;
    protected final QorvaUserDetailsService userService;
    protected final JwtConfig jwtConfig;

    protected AbstractQorvaController(QorvaService<D> service, QorvaRequestMapper<D> requestMapper, QorvaUserDetailsService userService, JwtConfig jwtConfig) {
        this.service = service;
		this.requestMapper = requestMapper;
		this.userService = userService;
		this.jwtConfig = jwtConfig;
	}

    @GetMapping("/{id}")
    public ResponseEntity<QorvaRequestResponse> findOneById(@PathVariable String id) throws QorvaException {
        return BuildApiResponse.from(this.service.findOneById(id));
    }

    @PostMapping("/search")
    public ResponseEntity<QorvaRequestResponse> findOneByData(@RequestHeader("Authorization") String authorizationHeader, @RequestBody D requestData) throws QorvaException {
        requestData.setTenantId(JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey()));
        return BuildApiResponse.from(this.service.findOneByData(requestData));
    }

    @PostMapping
    public ResponseEntity<QorvaRequestResponse> createOne(@RequestHeader("Authorization") String authorizationHeader, @RequestBody D data) throws QorvaException {
        data.setTenantId(JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey()));
        return BuildApiResponse.from(this.service.createOne(data));
    }

    @GetMapping(path = "/{tenantId}", produces = "application/json")
    public ResponseEntity<QorvaRequestResponse> findAll(
        @PathVariable("tenantId") String tenantId,
        @RequestParam(name = "pageSize", defaultValue = "50") int pageSize,
        @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber
    ) throws QorvaException {
        return BuildApiResponse.from(this.service.findAll(this.requestMapper.toDtoFromTenantID(tenantId), pageNumber, pageSize));
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<QorvaRequestResponse> findAll(@RequestHeader("Authorization") String authorizationHeader, @RequestParam Map<String, String> params) throws QorvaException {
        var tenantId = JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey());
        var data = this.requestMapper.toDto(params);
        var pageNumber = Integer.parseInt(params.getOrDefault("pageNumber", "0"));
        var pageSize = Integer.parseInt(params.getOrDefault("pageSize", "50"));
        data.setTenantId(tenantId);

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
    public ResponseEntity<QorvaRequestResponse> deleteOneById(@PathVariable String id, @RequestHeader("Authorization") String authorizationHeader) throws QorvaException {
        this.service.deleteOneById(id, JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey()));
        return BuildApiResponse.from(true);
    }

    @PostMapping("/exists")
    public ResponseEntity<QorvaRequestResponse> existsByData(@RequestBody D data) throws QorvaException {
        return BuildApiResponse.from(this.service.existsByData(data));
    }
}
