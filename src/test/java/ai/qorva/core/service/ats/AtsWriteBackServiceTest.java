package ai.qorva.core.service.ats;

import ai.qorva.core.dao.entity.AtsConnection;
import ai.qorva.core.dao.entity.AtsOutboundTask;
import ai.qorva.core.dao.repository.AtsConnectionRepository;
import ai.qorva.core.dao.repository.AtsOutboundTaskRepository;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.AtsRef;
import ai.qorva.core.dto.common.DecisionSummary;
import ai.qorva.core.dto.common.MatchingReportDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtsWriteBackServiceTest {

	@Mock private AtsOutboundTaskRepository taskRepository;
	@Mock private AtsConnectionRepository connectionRepository;
	@Mock private AtsConnectionService connectionService;
	@Mock private AtsConnectorRegistry registry;
	@Mock private AtsOauthService oauthService;
	@Mock private MongoTemplate mongoTemplate;

	private AtsWriteBackService service;

	@BeforeEach
	void setUp() {
		service = new AtsWriteBackService(taskRepository, connectionRepository, connectionService,
			registry, oauthService, mongoTemplate, "https://app.qorva.ai/");
	}

	private CVDTO linkedCv() {
		var cv = new CVDTO();
		cv.setId("cv-1");
		cv.setTenantId("64b0c1a2e4b0f2a1b2c3d4e5");
		cv.setAtsRefs(List.of(AtsRef.builder()
			.provider("greenhouse").connectionId("conn-1").externalId("17681532").build()));
		return cv;
	}

	private MatchingReportDetails report() {
		var summary = new DecisionSummary();
		summary.setFinalScore(82.0);
		summary.setReportHeadline("Strong match");
		var details = new MatchingReportDetails();
		details.setDecisionSummary(summary);
		return details;
	}

	private AtsConnection connectedWithWriteBack() {
		return AtsConnection.builder()
			.id("conn-1").provider("greenhouse").status(AtsConnection.STATUS_CONNECTED)
			.settings(AtsConnection.Settings.builder().writeBackScores(true).build())
			.build();
	}

	@Test
	void enqueuedNoteLinksToTheReportsScreenNotTheAppRoot() {
		when(connectionRepository.findByIdInTenant("conn-1", "64b0c1a2e4b0f2a1b2c3d4e5")).thenReturn(Optional.of(connectedWithWriteBack()));
		var jobPost = new JobPostDTO();
		jobPost.setTitle("Backend Engineer");

		service.maybeEnqueue(linkedCv(), jobPost, report());

		var task = ArgumentCaptor.forClass(AtsOutboundTask.class);
		verify(taskRepository).save(task.capture());
		assertThat(task.getValue().getReportUrl()).isEqualTo("https://app.qorva.ai/app/reports");
		assertThat(task.getValue().getStatus()).isEqualTo(AtsOutboundTask.STATUS_PENDING);
	}

	@Test
	void cvsThatDidNotComeFromAnAtsAreNeverPushed() {
		service.maybeEnqueue(new CVDTO(), new JobPostDTO(), report());

		verify(taskRepository, never()).save(any());
	}

	@Test
	void drainAlsoPicksUpTasksLeftInSending() {
		when(taskRepository.findByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
			anyList(), any(Instant.class), any(Pageable.class))).thenReturn(List.of());

		service.drain();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> statuses = ArgumentCaptor.forClass(List.class);
		verify(taskRepository).findByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
			statuses.capture(), any(Instant.class), any(Pageable.class));
		assertThat(statuses.getValue())
			.containsExactly(AtsOutboundTask.STATUS_PENDING, AtsOutboundTask.STATUS_SENDING);
	}

	@Test
	void claimingATaskLeasesItSoAParallelDrainSkipsIt() {
		var stuck = AtsOutboundTask.builder().id("task-1").status(AtsOutboundTask.STATUS_SENDING)
			.connectionId("conn-1").nextAttemptAt(Instant.now().minusSeconds(3600)).build();
		when(taskRepository.findByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
			anyList(), any(Instant.class), any(Pageable.class))).thenReturn(List.of(stuck));
		// Claim lost to another instance: nothing is sent.
		when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(AtsOutboundTask.class)))
			.thenReturn(null);

		service.drain();

		var query = ArgumentCaptor.forClass(Query.class);
		var update = ArgumentCaptor.forClass(Update.class);
		verify(mongoTemplate).findAndModify(query.capture(), update.capture(), any(), eq(AtsOutboundTask.class));
		// Only a task whose lease/backoff has elapsed is claimable, in either status.
		assertThat(query.getValue().getQueryObject().toString()).contains("SENDING", "PENDING", "nextAttemptAt");
		var applied = update.getValue().getUpdateObject().toString();
		assertThat(applied).contains("nextAttemptAt", "attempts");
		verify(connectionRepository, never()).findByIdInTenant(any(), any());
	}
}
