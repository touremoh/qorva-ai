package ai.qorva.core.service;

import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dto.UserDTO;
import ai.qorva.core.enums.EmailCategory;
import ai.qorva.core.enums.EmailTitlesEnum;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * "Copilot is waiting for your approval": the owner's rule runs waiting right now, one row per run, with a
 * link to Activity filtered on them. Every value from the data (rule names) is HTML-escaped.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "qorva.notifications", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentApprovalDigestNotificationService extends AbstractEmailService {

	static final String ACTIVITY_PATH = "/app/copilot?tab=activity&status=AWAITING_APPROVAL";
	private static final int MAX_ROWS = 20;

	private final MongoTemplate mongoTemplate;

	@Value("${qorva.assets.logo-url}")
	private String logoUrl;

	@Value("${weblink.appBaseUrl}")
	private String appBaseUrl;

	public AgentApprovalDigestNotificationService(OAuth2TokenService oauth2TokenService, EmailSenderResolver senderResolver,
	                                              MongoTemplate mongoTemplate) {
		super(oauth2TokenService, senderResolver);
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	protected EmailCategory getCategory() {
		return EmailCategory.UPDATES;
	}

	/** Needs the tenant of the runs; use {@link #send(String, UserDTO, String)}. */
	@Override
	public void send(UserDTO receiver, String languageCode) throws QorvaException {
		send(receiver.getTenantId(), receiver, languageCode);
	}

	/** Sends nothing when everything was decided between queueing and sending. */
	public void send(String tenantId, UserDTO receiver, String languageCode) throws QorvaException {
		var lang = languageCode != null ? languageCode : "en";
		var query = Query.query(Criteria.where("tenantId").is(new ObjectId(tenantId)).and("userEmail").is(receiver.getEmail())
				.and("origin").is(AgentRun.ORIGIN_RULE).and("status").is(AgentRun.STATUS_AWAITING_APPROVAL))
			.with(Sort.by("createdAt")).limit(MAX_ROWS);
		query.fields().include("ruleName", "title", "pendingActions.status", "tenantId");
		var runs = mongoTemplate.find(query, AgentRun.class);
		if (runs.isEmpty()) {
			log.info("Agent digest for {} skipped: nothing waits anymore", receiver.getEmail());
			return;
		}
		try {
			var wrapper = loadHtmlTemplate("templates/emails/user-added-template.html");
			var content = loadHtmlTemplate("templates/emails/" + lang + "_agent_approval_digest_content.html")
				.replace("{{logo_url}}", logoUrl)
				.replace("{{first_name}}", HtmlUtils.htmlEscape(receiver.getFirstName() != null ? receiver.getFirstName() : ""))
				.replace("{{app_name}}", "Qorva AI")
				.replace("{{pending_rows}}", rows(runs))
				.replace("{{activity_url}}", activityUrl())
				.replace("{{support_email}}", supportEmail())
				.replace("{{current_year}}", String.valueOf(LocalDate.now().getYear()));
			sendEmail(receiver.getEmail(), EmailTitlesEnum.getEmailTitle(lang, "agent_approval_digest"),
				wrapper.replace("{{template_content}}", content));
			log.info("Agent digest sent to {} ({} runs)", receiver.getEmail(), runs.size());
		} catch (IOException | MailException e) {
			log.error("Failed to send agent digest to {}", receiver.getEmail(), e);
			throw new QorvaException("Failed to send agent digest", e, HttpStatus.INTERNAL_SERVER_ERROR.value(),
				HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	static String rows(List<AgentRun> runs) {
		var html = new StringBuilder();
		for (var run : runs) {
			long waiting = run.getPendingActions().stream().filter(a -> AgentRun.PendingAction.PENDING.equals(a.getStatus())).count();
			var name = run.getRuleName() != null ? run.getRuleName() : run.getTitle();
			html.append("<tr><td style=\"padding: 8px 0; border-bottom: 1px solid #E2E8F0;\">")
				.append(HtmlUtils.htmlEscape(name != null ? name : ""))
				.append("</td><td align=\"right\" style=\"padding: 8px 0; border-bottom: 1px solid #E2E8F0; font-weight: 700;\">")
				.append(waiting)
				.append("</td></tr>");
		}
		return html.toString();
	}

	String activityUrl() {
		var base = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
		return base + ACTIVITY_PATH;
	}
}
