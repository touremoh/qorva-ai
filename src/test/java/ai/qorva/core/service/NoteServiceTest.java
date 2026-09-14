package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.entity.MatchingReport;
import ai.qorva.core.dao.entity.Note;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dao.repository.NoteRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.NoteRequest;
import ai.qorva.core.enums.NoteTargetTypeEnum;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.NoteMapper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

	private static final String TENANT = new ObjectId().toHexString();
	private static final String OTHER_TENANT = new ObjectId().toHexString();
	private static final String CV_ID = new ObjectId().toHexString();
	private static final String REPORT_ID = new ObjectId().toHexString();
	private static final String ALICE = "alice@acme.test";
	private static final String BOB = "bob@acme.test";

	@Mock private NoteRepository noteRepository;
	@Mock private CVRepository cvRepository;
	@Mock private MatchingReportRepository matchingReportRepository;
	@Mock private UserRepository userRepository;
	@Mock private QorvaApiAccessManager accessManager;

	private NoteService service;

	@BeforeEach
	void setUp() {
		service = new NoteService(noteRepository, new NoteMapperStub(), cvRepository,
			matchingReportRepository, userRepository, new NoteAccessManager(accessManager));
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void create_onOwnCv_persistsTrimmedTextWithAuthorName() throws QorvaException {
		when(cvRepository.findById(new ObjectId(CV_ID))).thenReturn(Optional.of(cv(TENANT)));
		var alice = new User();
		alice.setFirstName("Alice");
		alice.setLastName("Martin");
		when(userRepository.findByEmail(ALICE)).thenReturn(alice);
		when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

		var dto = service.create(TENANT, ALICE, new NoteRequest("cv", CV_ID, "  Strong on Kafka.  "));

		var saved = ArgumentCaptor.forClass(Note.class);
		verify(noteRepository).save(saved.capture());
		assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT);
		assertThat(saved.getValue().getTargetType()).isEqualTo("CV");
		assertThat(saved.getValue().getText()).isEqualTo("Strong on Kafka.");
		assertThat(saved.getValue().getAuthorName()).isEqualTo("Alice Martin");
		assertThat(dto.getText()).isEqualTo("Strong on Kafka.");
	}

	@Test
	void create_onAnotherTenantsReport_isNotFound_noExistenceOracle() {
		when(matchingReportRepository.findById(new ObjectId(REPORT_ID))).thenReturn(Optional.of(report(OTHER_TENANT)));

		assertThatThrownBy(() -> service.create(TENANT, ALICE, new NoteRequest("MATCHING_REPORT", REPORT_ID, "hello")))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.HTTP_NOT_FOUND);
		verify(noteRepository, never()).save(any());
	}

	@Test
	void create_rejectsBlankAndOversizedText() {
		assertThatThrownBy(() -> service.create(TENANT, ALICE, new NoteRequest("CV", CV_ID, "   ")))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.NOTE_TEXT_INVALID);
		assertThatThrownBy(() -> service.create(TENANT, ALICE, new NoteRequest("CV", CV_ID, "x".repeat(NoteService.MAX_TEXT_LENGTH + 1))))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.NOTE_TEXT_INVALID);
		verify(noteRepository, never()).save(any());
	}

	@Test
	void create_rejectsUnknownTargetType() {
		assertThatThrownBy(() -> service.create(TENANT, ALICE, new NoteRequest("JOB", CV_ID, "hello")))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.NOTE_TARGET_TYPE_INVALID);
	}

	@Test
	void delete_byAnotherUser_isForbiddenEvenWithModifyAuthority() {
		authenticateAs(BOB, "MODIFY_CV:ALLOWED");
		when(accessManager.hasPermission(any(), anyString())).thenReturn(true);
		var note = note(ALICE);
		when(noteRepository.findById(note.getId())).thenReturn(Optional.of(note));

		assertThatThrownBy(() -> service.delete(TENANT, BOB, note.getId()))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.NOTE_NOT_AUTHOR);
		verify(noteRepository, never()).delete(any());
	}

	@Test
	void delete_byAuthor_removesTheNote() throws QorvaException {
		authenticateAs(ALICE, "MODIFY_CV:ALLOWED");
		when(accessManager.hasPermission(any(), anyString())).thenReturn(true);
		var note = note(ALICE);
		when(noteRepository.findById(note.getId())).thenReturn(Optional.of(note));

		service.delete(TENANT, ALICE, note.getId());

		verify(noteRepository).delete(note);
	}

	@Test
	void update_byAuthorWithoutWriteAuthority_isForbidden() {
		authenticateAs(ALICE);
		when(accessManager.hasPermission(any(), anyString())).thenReturn(false);
		var note = note(ALICE);
		when(noteRepository.findById(note.getId())).thenReturn(Optional.of(note));

		assertThatThrownBy(() -> service.update(TENANT, ALICE, note.getId(), new NoteRequest(null, null, "edited")))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.HTTP_FORBIDDEN);
		verify(noteRepository, never()).save(any());
	}

	@Test
	void update_ofNoteFromAnotherTenant_isNotFound() {
		authenticateAs(ALICE);
		var note = note(ALICE);
		note.setTenantId(OTHER_TENANT);
		when(noteRepository.findById(note.getId())).thenReturn(Optional.of(note));

		assertThatThrownBy(() -> service.update(TENANT, ALICE, note.getId(), new NoteRequest(null, null, "edited")))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.NOTE_NOT_FOUND);
	}

	@Test
	void retarget_movesEveryNoteOfTheOldCvToTheNewOne() {
		var newCvId = new ObjectId().toHexString();
		var notes = List.of(note(ALICE), note(BOB));
		when(noteRepository.findByTenantIdAndTargetTypeAndTargetIdOrderByCreatedAtDesc(TENANT, "CV", CV_ID)).thenReturn(notes);

		var moved = service.retarget(TENANT, NoteTargetTypeEnum.CV, CV_ID, newCvId);

		assertThat(moved).isEqualTo(2);
		assertThat(notes).allSatisfy(n -> assertThat(n.getTargetId()).isEqualTo(newCvId));
		verify(noteRepository).saveAll(notes);
	}

	// -------------------------------------------------------------------------

	private static void authenticateAs(String email, String... authorities) {
		var auth = new UsernamePasswordAuthenticationToken(email, "n/a",
			List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	private static Note note(String author) {
		return Note.builder()
			.id(new ObjectId().toHexString())
			.tenantId(TENANT)
			.targetType("CV")
			.targetId(CV_ID)
			.text("a note")
			.authorEmail(author)
			.authorName(author)
			.build();
	}

	private static CV cv(String tenantId) {
		var cv = new CV();
		cv.setId(CV_ID);
		cv.setTenantId(tenantId);
		return cv;
	}

	private static MatchingReport report(String tenantId) {
		var r = new MatchingReport();
		r.setId(REPORT_ID);
		r.setTenantId(tenantId);
		return r;
	}

	/** MapStruct's generated impl is not on the test classpath without a full build; a hand map is enough. */
	private static final class NoteMapperStub implements NoteMapper {
		@Override
		public ai.qorva.core.dto.NoteDTO map(Note n) {
			return ai.qorva.core.dto.NoteDTO.builder()
				.id(n.getId()).targetType(n.getTargetType()).targetId(n.getTargetId()).text(n.getText())
				.authorName(n.getAuthorName()).authorEmail(n.getAuthorEmail())
				.createdAt(n.getCreatedAt()).lastUpdatedAt(n.getLastUpdatedAt())
				.build();
		}
	}
}
