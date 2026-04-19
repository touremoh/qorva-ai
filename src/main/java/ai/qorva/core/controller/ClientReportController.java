package ai.qorva.core.controller;

import ai.qorva.core.dto.ClientReportDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.requests.ClientReportRequestMapper;
import ai.qorva.core.service.ClientReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/reports")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class ClientReportController extends AbstractQorvaController<ClientReportDTO> {

	@Autowired
	protected ClientReportController(ClientReportService service, ClientReportRequestMapper requestMapper) {
		super(service, requestMapper);
	}

	@GetMapping(value = "/download", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'ATS_REPORT_EXPORT')")
	public ResponseEntity<Resource> downloadCsv() {
		log.info("Generating ATS export");
		String csvData = "ID,Name,Email\n1,John Doe,john@example.com";
		byte[] data = csvData.getBytes();
		ByteArrayResource resource = new ByteArrayResource(data);

		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users.csv")
			.contentType(MediaType.parseMediaType("text/csv"))
			.contentLength(data.length)
			.body(resource);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_REPORT')")
	public ResponseEntity<QorvaRequestResponse> findOneById(String id) throws QorvaException {
		return super.findOneById(id);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_REPORT')")
	public ResponseEntity<QorvaRequestResponse> findOneByData(ClientReportDTO requestData) throws QorvaException {
		return super.findOneByData(requestData);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'GENERATE_REPORT')")
	public ResponseEntity<QorvaRequestResponse> createOne(ClientReportDTO data) throws QorvaException {
		return super.createOne(data);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_REPORT')")
	public ResponseEntity<QorvaRequestResponse> findAll(Map<String, String> params) throws QorvaException {
		return super.findAll(params);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_REPORT')")
	public ResponseEntity<QorvaRequestResponse> findManyByIds(List<String> ids) throws QorvaException {
		return super.findManyByIds(ids);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'MODIFY_REPORT')")
	public ResponseEntity<QorvaRequestResponse> updateOne(String id, ClientReportDTO data) throws QorvaException {
		return super.updateOne(id, data);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'MODIFY_REPORT')")
	public ResponseEntity<QorvaRequestResponse> patchOne(String id, ClientReportDTO data) throws QorvaException {
		return super.patchOne(id, data);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'DELETE_REPORT')")
	public ResponseEntity<QorvaRequestResponse> deleteOneById(String id) throws QorvaException {
		return super.deleteOneById(id);
	}

	@Override
	@PreAuthorize("@accessManager.hasAuthority(authentication, 'VIEW_REPORT')")
	public ResponseEntity<QorvaRequestResponse> existsByData(ClientReportDTO data) throws QorvaException {
		return super.existsByData(data);
	}
}
