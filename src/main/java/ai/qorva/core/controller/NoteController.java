package ai.qorva.core.controller;

import ai.qorva.core.dto.NoteDTO;
import ai.qorva.core.dto.NoteRequest;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Recruiter notes on CVs and matching reports. Reads and writes are gated by the target's own
 * authorities (see {@code NoteAccessManager}); editing and deleting are additionally restricted to
 * the note's author inside the service.
 */
@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class NoteController {

	private final NoteService noteService;

	private String currentTenantId() {
		return TenantContextHolder.getTenantId();
	}

	private String currentUsername() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	@GetMapping
	@PreAuthorize("@noteAccess.canRead(authentication, #targetType)")
	public ResponseEntity<List<NoteDTO>> list(@RequestParam String targetType,
	                                          @RequestParam String targetId) throws QorvaException {
		return ResponseEntity.ok(noteService.list(currentTenantId(), targetType, targetId));
	}

	@PostMapping
	@PreAuthorize("@noteAccess.canWrite(authentication, #request.targetType)")
	public ResponseEntity<NoteDTO> create(@RequestBody NoteRequest request) throws QorvaException {
		var created = noteService.create(currentTenantId(), currentUsername(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PutMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<NoteDTO> update(@PathVariable String id, @RequestBody NoteRequest request) throws QorvaException {
		return ResponseEntity.ok(noteService.update(currentTenantId(), currentUsername(), id, request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Void> delete(@PathVariable String id) throws QorvaException {
		noteService.delete(currentTenantId(), currentUsername(), id);
		return ResponseEntity.noContent().build();
	}
}
