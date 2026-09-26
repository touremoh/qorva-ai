package ai.qorva.core.service;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.entity.CandidateOutreach;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.CandidateOutreachRepository;
import ai.qorva.core.dao.repository.SuppressedEmailRepository;
import ai.qorva.core.dao.repository.UserRepository;
import ai.qorva.core.dto.CandidateOutreachData;
import ai.qorva.core.dto.CandidateOutreachData.MailboxState;
import ai.qorva.core.dto.common.Contact;
import ai.qorva.core.dto.common.PersonalInformation;
import ai.qorva.core.exception.QorvaErrorCodes;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.CandidateOutreachMapper;
import ai.qorva.core.service.mailbox.MailboxConnectionService;
import ai.qorva.core.service.mailbox.MailboxSender;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateOutreachServiceTest {

	private static final String TENANT = "64b0c1a2e4b0f2a1b2c3d4e5";
	private static final String CV_ID = new ObjectId().toHexString();
	private static final String USERNAME = "jane@acme.test";

	@Mock private CandidateOutreachRepository repository;
	@Mock private CVRepository cvRepository;
	@Mock private SuppressedEmailRepository suppressedEmailRepository;
	@Mock private MailboxConnectionService mailboxConnectionService;
	@Mock private UserRepository userRepository;

	private CandidateOutreachService service;

	@BeforeEach
	void setUp() {
		service = new CandidateOutreachService(repository, Mappers.getMapper(CandidateOutreachMapper.class),
			cvRepository, suppressedEmailRepository, mailboxConnectionService, userRepository);
	}

	private CV cvWithEmail(String email) {
		var cv = new CV();
		cv.setId(CV_ID);
		cv.setTenantId(TENANT);
		var info = new PersonalInformation();
		info.setName("Ada Lovelace");
		var contact = new Contact();
		contact.setEmail(email);
		info.setContact(contact);
		cv.setPersonalInformation(info);
		return cv;
	}

	@Test
	void contextReportsEmailSuppressionMailboxAndHistory() throws QorvaException {
		when(cvRepository.findByIdInTenant(CV_ID, TENANT)).thenReturn(Optional.of(cvWithEmail(" Ada@Example.com ")));
		when(suppressedEmailRepository.existsByTenantIdAndEmail(TENANT, "ada@example.com")).thenReturn(true);
		when(mailboxConnectionService.composerState(TENANT, USERNAME))
			.thenReturn(new MailboxConnectionService.ComposerState(MailboxState.MICROSOFT, "jane@acme.test"));
		when(repository.findByTenantIdAndCvIdOrderByCreatedAtDesc(eq(TENANT), eq(CV_ID), any(Pageable.class)))
			.thenReturn(List.of(CandidateOutreach.builder().id("o1").cvId(CV_ID).status("SENT").build()));

		var context = service.context(TENANT, USERNAME, CV_ID);

		assertThat(context.candidateName()).isEqualTo("Ada Lovelace");
		assertThat(context.email()).isEqualTo("Ada@Example.com");
		assertThat(context.suppressed()).isTrue();
		assertThat(context.mailbox()).isEqualTo(MailboxState.MICROSOFT);
		assertThat(context.mailboxAddress()).isEqualTo("jane@acme.test");
		assertThat(context.history()).extracting("id").containsExactly("o1");
	}

	@Test
	void foreignTenantCvReadsAsNotFound() {
		// The CV exists in another tenant; looked up in the caller's tenant it is not found.
		when(cvRepository.findByIdInTenant(CV_ID, TENANT)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.context(TENANT, USERNAME, CV_ID))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.HTTP_NOT_FOUND);
	}

	@Test
	void externalHandoffRefusesSuppressedAddress() {
		when(cvRepository.findByIdInTenant(CV_ID, TENANT)).thenReturn(Optional.of(cvWithEmail("ada@example.com")));
		when(suppressedEmailRepository.existsByTenantIdAndEmail(TENANT, "ada@example.com")).thenReturn(true);
		var request = new CandidateOutreachData.ExternalRequest();
		request.setCvId(CV_ID);
		request.setVia("GMAIL");
		request.setTo("ada@example.com");

		assertThatThrownBy(() -> service.recordExternal(TENANT, USERNAME, request))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.OUTREACH_SUPPRESSED);
		verify(repository, never()).save(any());
	}

	@Test
	void externalHandoffOnlyAcceptsHandoffClients() {
		var request = new CandidateOutreachData.ExternalRequest();
		request.setCvId(CV_ID);
		request.setVia("CONNECTED_MICROSOFT");
		request.setTo("ada@example.com");

		assertThatThrownBy(() -> service.recordExternal(TENANT, USERNAME, request))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.OUTREACH_VIA_INVALID);
	}

	@Test
	void externalHandoffIsRecordedWithSenderName() throws QorvaException {
		when(cvRepository.findByIdInTenant(CV_ID, TENANT)).thenReturn(Optional.of(cvWithEmail("ada@example.com")));
		when(suppressedEmailRepository.existsByTenantIdAndEmail(anyString(), anyString())).thenReturn(false);
		var user = new User();
		user.setFirstName("Jane");
		user.setLastName("Doe");
		when(userRepository.findByEmail(USERNAME)).thenReturn(user);
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		var request = new CandidateOutreachData.ExternalRequest();
		request.setCvId(CV_ID);
		request.setVia("gmail");
		request.setTo("ada@example.com");
		request.setSubject("Hello");
		request.setBody("Body");

		var dto = service.recordExternal(TENANT, USERNAME, request);

		var captor = ArgumentCaptor.forClass(CandidateOutreach.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(CandidateOutreach.STATUS_EXTERNAL_OPENED);
		assertThat(captor.getValue().getVia()).isEqualTo("GMAIL");
		assertThat(captor.getValue().getChannel()).isEqualTo(CandidateOutreach.CHANNEL_EMAIL);
		assertThat(captor.getValue().getSenderName()).isEqualTo("Jane Doe");
		assertThat(dto.getTo()).isEqualTo("ada@example.com");
	}

	@Test
	void sendLogsSentRowWithProviderIds() throws QorvaException {
		when(cvRepository.findByIdInTenant(CV_ID, TENANT)).thenReturn(Optional.of(cvWithEmail("ada@example.com")));
		when(suppressedEmailRepository.existsByTenantIdAndEmail(anyString(), anyString())).thenReturn(false);
		when(mailboxConnectionService.send(TENANT, USERNAME, "ada@example.com", "Hi", "Body"))
			.thenReturn(new MailboxSender.SendResult("msg-1", "conv-1", "https://outlook.office.com/x"));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		var request = new CandidateOutreachData.SendRequest();
		request.setCvId(CV_ID);
		request.setTo("ada@example.com");
		request.setSubject("Hi");
		request.setBody("Body");

		var response = service.send(TENANT, USERNAME, request);

		var captor = ArgumentCaptor.forClass(CandidateOutreach.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(CandidateOutreach.STATUS_SENT);
		assertThat(captor.getValue().getVia()).isEqualTo("CONNECTED_MICROSOFT");
		assertThat(captor.getValue().getProviderMessageId()).isEqualTo("msg-1");
		assertThat(response.providerWebLink()).isEqualTo("https://outlook.office.com/x");
	}

	@Test
	void sendFailureIsLoggedAsFailedAndRethrown() throws QorvaException {
		when(cvRepository.findByIdInTenant(CV_ID, TENANT)).thenReturn(Optional.of(cvWithEmail("ada@example.com")));
		when(suppressedEmailRepository.existsByTenantIdAndEmail(anyString(), anyString())).thenReturn(false);
		when(mailboxConnectionService.send(any(), any(), any(), any(), any()))
			.thenThrow(new QorvaException(QorvaErrorCodes.MAILBOX_REAUTH_REQUIRED, 409, org.springframework.http.HttpStatus.CONFLICT));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		var request = new CandidateOutreachData.SendRequest();
		request.setCvId(CV_ID);
		request.setTo("ada@example.com");
		request.setSubject("Hi");
		request.setBody("Body");

		assertThatThrownBy(() -> service.send(TENANT, USERNAME, request))
			.isInstanceOf(QorvaException.class)
			.hasMessage(QorvaErrorCodes.MAILBOX_REAUTH_REQUIRED);

		var captor = ArgumentCaptor.forClass(CandidateOutreach.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(CandidateOutreach.STATUS_FAILED);
		assertThat(captor.getValue().getError()).isEqualTo(QorvaErrorCodes.MAILBOX_REAUTH_REQUIRED);
	}

	@Test
	void missingConnectionIsNotLoggedAsAnAttempt() throws QorvaException {
		when(cvRepository.findByIdInTenant(CV_ID, TENANT)).thenReturn(Optional.of(cvWithEmail("ada@example.com")));
		when(suppressedEmailRepository.existsByTenantIdAndEmail(anyString(), anyString())).thenReturn(false);
		when(mailboxConnectionService.send(any(), any(), any(), any(), any()))
			.thenThrow(new QorvaException(QorvaErrorCodes.MAILBOX_NOT_CONNECTED, 404, org.springframework.http.HttpStatus.NOT_FOUND));
		var request = new CandidateOutreachData.SendRequest();
		request.setCvId(CV_ID);
		request.setTo("ada@example.com");
		request.setSubject("Hi");
		request.setBody("Body");

		assertThatThrownBy(() -> service.send(TENANT, USERNAME, request)).isInstanceOf(QorvaException.class);
		verify(repository, never()).save(any());
	}

	@Test
	void retargetMovesRowsToTheSurvivingCv() {
		var row = CandidateOutreach.builder().cvId("old").build();
		when(repository.findByTenantIdAndCvId(TENANT, "old")).thenReturn(List.of(row));

		assertThat(service.retarget(TENANT, "old", "new")).isEqualTo(1);
		assertThat(row.getCvId()).isEqualTo("new");
		verify(repository).saveAll(List.of(row));
	}
}
