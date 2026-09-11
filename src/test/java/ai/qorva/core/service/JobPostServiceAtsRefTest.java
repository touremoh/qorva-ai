package ai.qorva.core.service;

import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.querybuilder.JobPostQueryBuilder;
import ai.qorva.core.dao.repository.ChatsRepository;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dao.repository.MatchingReportRepository;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.AtsRef;
import ai.qorva.core.mapper.JobPostMapperImpl;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * updateOne rewrites the whole document from the DTO, so a field absent from JobPostDTO is
 * erased in Mongo. atsRef used to be one, which unlinked every imported job the moment a
 * recruiter edited it — and left its ATS-xxxx jobReference occupying the unique index, so the
 * next sync could neither find the job nor insert it, and the run died on a duplicate key.
 */
@ExtendWith(MockitoExtension.class)
class JobPostServiceAtsRefTest {

	private static final String JOB_ID = "6aa46a8683dcb9db7e98da40";
	private static final String TENANT_ID = "6a654169f1cbc9a5ae47405b";

	@Mock private JobPostRepository repository;
	@Mock private JobPostQueryBuilder queryBuilder;
	@Mock private MatchingReportRepository matchingReportRepository;
	@Mock private ChatsRepository chatsRepository;

	private JobPostService service;

	@BeforeEach
	void setUp() {
		// The real generated mapper: the point of the test is what mapping actually carries over.
		service = new JobPostService(repository, new JobPostMapperImpl(), queryBuilder,
			matchingReportRepository, chatsRepository);
	}

	private JobPost storedImportedJob() {
		var stored = new JobPost();
		stored.setId(JOB_ID);
		stored.setTenantId(TENANT_ID);
		stored.setTitle("Senior Marketer");
		stored.setJobReference("ATS-2744061");
		stored.setStatus("open");
		stored.setAtsRef(AtsRef.builder()
			.provider("recruitee")
			.connectionId("6aa4696283dcb9db7e98da3c")
			.externalId("2744061")
			.lastImportedAt(Instant.parse("2026-09-11T20:54:30Z"))
			.build());
		return stored;
	}

	private JobPost saveUpdate(JobPostDTO payload) throws Exception {
		when(repository.findById(new ObjectId(JOB_ID))).thenReturn(Optional.of(storedImportedJob()));
		when(repository.save(any(JobPost.class))).thenAnswer(call -> call.getArgument(0));

		service.updateOne(JOB_ID, payload);

		var captor = ArgumentCaptor.forClass(JobPost.class);
		verify(repository).save(captor.capture());
		return captor.getValue();
	}

	private static JobPostDTO editPayload() {
		var payload = new JobPostDTO();
		payload.setId(JOB_ID);
		payload.setTitle("Senior Marketer (edited)");
		payload.setStatus("open");
		return payload;
	}

	@Test
	void editingAnImportedJobKeepsItsAtsLink() throws Exception {
		var saved = saveUpdate(editPayload());

		assertThat(saved.getTitle()).isEqualTo("Senior Marketer (edited)");
		assertThat(saved.getAtsRef()).isNotNull();
		assertThat(saved.getAtsRef().getExternalId()).isEqualTo("2744061");
		assertThat(saved.getAtsRef().getProvider()).isEqualTo("recruitee");
	}

	/** The link is the sync engine's to set; a payload cannot repoint it at another record. */
	@Test
	void aClientCannotRewriteTheAtsLink() throws Exception {
		var payload = editPayload();
		payload.setAtsRef(AtsRef.builder().provider("greenhouse").externalId("999").build());

		var saved = saveUpdate(payload);

		assertThat(saved.getAtsRef().getProvider()).isEqualTo("recruitee");
		assertThat(saved.getAtsRef().getExternalId()).isEqualTo("2744061");
	}
}
