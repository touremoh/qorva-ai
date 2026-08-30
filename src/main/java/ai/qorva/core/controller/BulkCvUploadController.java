package ai.qorva.core.controller;

import ai.qorva.core.dto.BackgroundJobData;
import ai.qorva.core.dto.BulkCvUploadData;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.BulkCvUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Asynchronous bulk CV import. The client creates a draft job, stages files in chunks
 * (each chunk is S3-only — fast and safe within request timeouts), then starts the job;
 * extraction runs in the BackgroundJobWorker and progress is polled via GET.
 */
@RestController
@RequestMapping("/cvs/bulk-uploads")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class BulkCvUploadController {

	private final BulkCvUploadService bulkCvUploadService;

	@Autowired
	public BulkCvUploadController(BulkCvUploadService bulkCvUploadService) {
		this.bulkCvUploadService = bulkCvUploadService;
	}

	@PostMapping(produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'ADD_CV')")
	public ResponseEntity<BulkCvUploadData.CreateResponse> create(
		@AuthenticationPrincipal UserDetails userDetails) throws QorvaException {
		return ResponseEntity.status(HttpStatus.CREATED).body(bulkCvUploadService.create(
			TenantContextHolder.getTenantId(),
			userDetails != null ? userDetails.getUsername() : null));
	}

	@PostMapping(path = "/{jobId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'ADD_CV')")
	public ResponseEntity<BulkCvUploadData.StageResponse> stageFiles(
		@PathVariable String jobId,
		@RequestParam("files") List<MultipartFile> files) throws QorvaException {
		return ResponseEntity.ok(bulkCvUploadService.appendFiles(TenantContextHolder.getTenantId(), jobId, files));
	}

	@PostMapping(path = "/{jobId}/start", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'ADD_CV')")
	public ResponseEntity<BulkCvUploadData.StartResponse> start(@PathVariable String jobId) throws QorvaException {
		return ResponseEntity.accepted().body(bulkCvUploadService.start(TenantContextHolder.getTenantId(), jobId));
	}

	@GetMapping(produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_CV')")
	public ResponseEntity<BackgroundJobData.JobList> list() {
		return ResponseEntity.ok(bulkCvUploadService.list(TenantContextHolder.getTenantId()));
	}

	@GetMapping(path = "/{jobId}", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'VIEW_CV')")
	public ResponseEntity<BackgroundJobData.JobView> get(@PathVariable String jobId) throws QorvaException {
		return ResponseEntity.ok(bulkCvUploadService.get(TenantContextHolder.getTenantId(), jobId));
	}

	@PostMapping(path = "/{jobId}/cancel", produces = "application/json")
	@PreAuthorize("@accessManager.hasPermission(authentication,'ADD_CV')")
	public ResponseEntity<BackgroundJobData.JobView> cancel(@PathVariable String jobId) throws QorvaException {
		return ResponseEntity.ok(bulkCvUploadService.cancel(TenantContextHolder.getTenantId(), jobId));
	}
}
