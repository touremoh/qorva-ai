package ai.qorva.core.service;

import ai.qorva.core.dao.entity.Note;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dao.repository.NoteRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.NoteDTO;
import ai.qorva.core.dto.NoteRequest;
import ai.qorva.core.enums.NoteTargetTypeEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.NoteMapper;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Recruiter notes on CVs and matching reports. Every note is visible to the whole tenant; only its
 * author may edit or delete it, whatever other authorities they hold. Notes never touch the
 * annotated documents (see {@link Note}) and are removed by the owning service when a CV, report or
 * job post goes away.
 */
@Slf4j
@Service
public class NoteService {

	static final int MAX_TEXT_LENGTH = 4000;
	private static final int MAX_THREAD_SIZE = 200;

	private final NoteRepository noteRepository;
	private final NoteMapper noteMapper;
	private final CVRepository cvRepository;
	private final MatchingReportRepository matchingReportRepository;
	private final UserRepository userRepository;
	private final NoteAccessManager noteAccess;

	public NoteService(NoteRepository noteRepository, NoteMapper noteMapper, CVRepository cvRepository,
	                   MatchingReportRepository matchingReportRepository, UserRepository userRepository,
	                   NoteAccessManager noteAccess) {
		this.noteRepository = noteRepository;
		this.noteMapper = noteMapper;
		this.cvRepository = cvRepository;
		this.matchingReportRepository = matchingReportRepository;
		this.userRepository = userRepository;
		this.noteAccess = noteAccess;
	}

	public List<NoteDTO> list(String tenantId, String targetType, String targetId) throws QorvaException {
		var type = parseType(targetType);
		var notes = noteRepository.findByTenantIdAndTargetTypeAndTargetIdOrderByCreatedAtDesc(tenantId, type.name(), targetId);
		return notes.stream().limit(MAX_THREAD_SIZE).map(noteMapper::map).toList();
	}

	public NoteDTO create(String tenantId, String authorEmail, NoteRequest request) throws QorvaException {
		var type = parseType(request.getTargetType());
		var text = cleanText(request.getText());
		assertTargetBelongsToTenant(tenantId, type, request.getTargetId());

		var note = Note.builder()
			.tenantId(tenantId)
			.targetType(type.name())
			.targetId(request.getTargetId())
			.text(text)
			.authorName(resolveAuthorName(authorEmail))
			.build();
		var saved = noteRepository.save(note);
		log.info("Note {} added on {} {} by {}", saved.getId(), type, request.getTargetId(), authorEmail);
		return noteMapper.map(saved);
	}

	public NoteDTO update(String tenantId, String authorEmail, String noteId, NoteRequest request) throws QorvaException {
		var note = loadOwn(tenantId, authorEmail, noteId);
		note.setText(cleanText(request.getText()));
		return noteMapper.map(noteRepository.save(note));
	}

	public void delete(String tenantId, String authorEmail, String noteId) throws QorvaException {
		var note = loadOwn(tenantId, authorEmail, noteId);
		noteRepository.delete(note);
		log.info("Note {} deleted by {}", noteId, authorEmail);
	}

	// -------------------------------------------------------------------------
	// Cascade helpers — called by the services that own the annotated documents
	// -------------------------------------------------------------------------

	public long deleteForTarget(String tenantId, NoteTargetTypeEnum type, String targetId) {
		return noteRepository.deleteByTenantIdAndTargetTypeAndTargetId(tenantId, type.name(), targetId);
	}

	public long deleteForTargets(String tenantId, NoteTargetTypeEnum type, Collection<String> targetIds) {
		if (targetIds == null || targetIds.isEmpty()) return 0L;
		return noteRepository.deleteByTenantIdAndTargetTypeAndTargetIdIn(tenantId, type.name(), targetIds);
	}

	public long deleteForTenant(String tenantId) {
		return noteRepository.deleteByTenantId(tenantId);
	}

	/** Moves a thread to another document of the same type (duplicate resolution keeps recruiter knowledge). */
	public long retarget(String tenantId, NoteTargetTypeEnum type, String fromTargetId, String toTargetId) {
		var notes = noteRepository.findByTenantIdAndTargetTypeAndTargetIdOrderByCreatedAtDesc(tenantId, type.name(), fromTargetId);
		if (notes.isEmpty()) return 0L;
		notes.forEach(n -> n.setTargetId(toTargetId));
		noteRepository.saveAll(notes);
		return notes.size();
	}

	// -------------------------------------------------------------------------

	/** Loads a note for edit/delete: must exist in this tenant, be the caller's own, and the caller must hold the target's write authority. */
	private Note loadOwn(String tenantId, String authorEmail, String noteId) throws QorvaException {
		var note = noteRepository.findById(noteId)
			.filter(n -> Objects.equals(n.getTenantId(), tenantId))
			.orElseThrow(() -> new QorvaException(QorvaErrorCodes.NOTE_NOT_FOUND,
				HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND));

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (!noteAccess.canWrite(auth, NoteTargetTypeEnum.fromValue(note.getTargetType()))) {
			throw new QorvaException(QorvaErrorCodes.HTTP_FORBIDDEN, HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN);
		}
		if (!Objects.equals(note.getAuthorEmail(), authorEmail)) {
			log.warn("User {} tried to modify note {} owned by {}", authorEmail, noteId, note.getAuthorEmail());
			throw new QorvaException(QorvaErrorCodes.NOTE_NOT_AUTHOR, HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN);
		}
		return note;
	}

	private void assertTargetBelongsToTenant(String tenantId, NoteTargetTypeEnum type, String targetId) throws QorvaException {
		if (!ObjectId.isValid(targetId)) {
			throw notFound();
		}
		var oid = new ObjectId(targetId);
		var owner = switch (type) {
			case CV -> cvRepository.findById(oid).map(cv -> cv.getTenantId()).orElse(null);
			case MATCHING_REPORT -> matchingReportRepository.findById(oid).map(r -> r.getTenantId()).orElse(null);
		};
		// A wrong tenant and a missing document answer the same way: no existence oracle.
		if (!Objects.equals(owner, tenantId)) {
			throw notFound();
		}
	}

	private String resolveAuthorName(String email) {
		var user = userRepository.findByEmail(email);
		if (user == null) return email;
		var name = ((StringUtils.hasText(user.getFirstName()) ? user.getFirstName() : "") + " "
			+ (StringUtils.hasText(user.getLastName()) ? user.getLastName() : "")).trim();
		return StringUtils.hasText(name) ? name : email;
	}

	private static NoteTargetTypeEnum parseType(String value) throws QorvaException {
		var type = NoteTargetTypeEnum.fromValue(value);
		if (type == null) {
			throw new QorvaException(QorvaErrorCodes.NOTE_TARGET_TYPE_INVALID,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}
		return type;
	}

	private static String cleanText(String text) throws QorvaException {
		var cleaned = text == null ? "" : text.strip();
		if (cleaned.isEmpty() || cleaned.length() > MAX_TEXT_LENGTH) {
			throw new QorvaException(QorvaErrorCodes.NOTE_TEXT_INVALID,
				HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST);
		}
		return cleaned;
	}

	private static QorvaException notFound() {
		return new QorvaException(QorvaErrorCodes.HTTP_NOT_FOUND, HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND);
	}
}
