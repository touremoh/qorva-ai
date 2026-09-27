package ai.qorva.core.it;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.config.QorvaProductProperties;
import ai.qorva.core.dao.entity.CandidateEmailTemplate;
import ai.qorva.core.dao.entity.Chat;
import ai.qorva.core.dao.entity.ChatMessage;
import ai.qorva.core.dao.entity.InsightConversationTurn;
import ai.qorva.core.dao.entity.Note;
import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.InsightIntent;
import ai.qorva.core.dto.InsightResponseDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.common.ChatContext;
import ai.qorva.core.dto.common.ChatMetadata;
import ai.qorva.core.dto.common.DecisionSummary;
import ai.qorva.core.dto.common.MatchingReportDetails;
import ai.qorva.core.dto.common.Participant;
import ai.qorva.core.dto.common.SkillsMatch;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.dto.common.UserAuthority;
import ai.qorva.core.enums.ChatStatus;
import ai.qorva.core.enums.ChatUserRole;
import ai.qorva.core.helpers.UserAuthoritiesHelper;
import ai.qorva.core.security.TenantContextHolder;
import ai.qorva.core.service.CVService;
import ai.qorva.core.service.DemoFixtureTokens;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.MatchingReportService;
import ai.qorva.core.service.QorvaUserDetailsService;
import ai.qorva.core.service.UsageMonitoringService;
import ai.qorva.core.utils.JwtUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seeds two complete, independent tenants through the production services (the same path the demo
 * seeding takes), then pins every createdAt/lastUpdatedAt so list orders and response bodies are
 * identical from one run to the next.
 *
 * <p>Tenant A is a tech recruiter, tenant B a finance recruiter, so any cross-tenant leak shows up as
 * the other segment's data. Dates inside the fixtures are resolved against {@link DemoFixtureTokens#FIXED_MONTH}.</p>
 */
@Component
public class TwoTenantFixture {

	public static final String PASSWORD = "Correct-Horse-9";
	public static final Instant BASE_TIME = Instant.parse("2026-09-15T08:00:00Z");

	/** Mongock's own collections; everything else (the global catalogue and ledger included) is wiped before each seed. */
	private static final Set<String> KEPT_COLLECTIONS = Set.of("mongockChangeLog", "mongockLock");

	private final MongoTemplate mongo;
	private final CVService cvService;
	private final JobPostService jobPostService;
	private final MatchingReportService matchingReportService;
	private final UsageMonitoringService usageMonitoringService;
	private final QorvaProductProperties productProperties;
	private final PasswordEncoder passwordEncoder;
	private final QorvaUserDetailsService userDetailsService;
	private final JwtConfig jwtConfig;
	private final ObjectMapper objectMapper;

	public TwoTenantFixture(MongoTemplate mongo, CVService cvService, JobPostService jobPostService,
	                        MatchingReportService matchingReportService, UsageMonitoringService usageMonitoringService,
	                        QorvaProductProperties productProperties, PasswordEncoder passwordEncoder,
	                        QorvaUserDetailsService userDetailsService, JwtConfig jwtConfig, ObjectMapper objectMapper) {
		this.mongo = mongo;
		this.cvService = cvService;
		this.jobPostService = jobPostService;
		this.matchingReportService = matchingReportService;
		this.usageMonitoringService = usageMonitoringService;
		this.productProperties = productProperties;
		this.passwordEncoder = passwordEncoder;
		this.userDetailsService = userDetailsService;
		this.jwtConfig = jwtConfig;
		this.objectMapper = objectMapper;
	}

	/** Everything a test needs to address one seeded tenant. */
	public record SeededTenant(
		String tenantId, String tenantName,
		String ownerId, String ownerEmail,
		String viewerId, String viewerEmail,
		List<String> cvIds, List<String> jobIds, List<String> reportIds,
		String noteId, String templateId, String chatId, String conversationId
	) {
		public String cvId() { return cvIds.getFirst(); }
		public String jobId() { return jobIds.getFirst(); }
		public String reportId() { return reportIds.getFirst(); }
	}

	public record Seeded(SeededTenant a, SeededTenant b) {}

	public Seeded reset() {
		for (var name : mongo.getCollectionNames()) {
			if (!KEPT_COLLECTIONS.contains(name) && !name.startsWith("system.")) {
				mongo.getCollection(name).deleteMany(new Document());
			}
		}
		try {
			var a = seedTenant("Acme Tech Talent", "a", "tech-it");
			var b = seedTenant("Borealis Finance Search", "b", "finance-accounting");
			pinTimestamps();
			return new Seeded(a, b);
		} catch (Exception e) {
			throw new IllegalStateException("Could not seed the two-tenant fixture", e);
		} finally {
			TenantContextHolder.clear();
			SecurityContextHolder.clearContext();
		}
	}

	/** Every document of the tenant, in every collection, as one string: any change to its data shows up here. */
	public String fingerprint(String tenantId) {
		var sb = new StringBuilder();
		var tenant = new ObjectId(tenantId);
		for (var name : mongo.getCollectionNames().stream().sorted().toList()) {
			if (name.startsWith("mongock") || name.startsWith("system.")) continue;
			var filter = new Document("$or", List.of(
				new Document("tenantId", tenant), new Document("tenantId", tenantId), new Document("_id", tenant)));
			for (var doc : mongo.getCollection(name).find(filter).sort(new Document("_id", 1))) {
				sb.append(name).append(':').append(doc.toJson()).append('\n');
			}
		}
		return sb.toString();
	}

	/** A signed access token for the user, built exactly as login builds it. */
	public String bearer(String email, String tenantId) {
		var userDetails = userDetailsService.loadUserByUsername(email);
		var tenant = mongo.findById(new ObjectId(tenantId), Tenant.class);
		var tenantDto = new TenantDTO();
		tenantDto.setId(tenantId);
		tenantDto.setSubscriptionInfo(tenant.getSubscriptionInfo());
		return "Bearer " + JwtUtils.generateToken(userDetails, jwtConfig, tenantDto);
	}

	private SeededTenant seedTenant(String name, String key, String segment) throws Exception {
		var tenantId = insertTenant(name);
		TenantContextHolder.setTenantId(tenantId);

		var ownerEmail = "owner@" + key + ".qorva.test";
		// Seed as the tenant owner, so @CreatedBy/@LastModifiedBy (and a note's author) are what the
		// owner's own actions would have written, not the "qorva" system fallback.
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(ownerEmail, null, List.of()));
		var viewerEmail = "viewer@" + key + ".qorva.test";
		var ownerId = insertUser(tenantId, "Olivia", "Owner-" + key.toUpperCase(), ownerEmail, UserAuthoritiesHelper.createAuthorities());
		var viewerId = insertUser(tenantId, "Victor", "Viewer-" + key.toUpperCase(), viewerEmail, viewerAuthorities());

		var cvIds = new ArrayList<String>();
		for (var cv : fixtures(segment, "cvs.json", new TypeReference<List<CVDTO>>() {})) {
			cv.setId(null);
			cv.setTenantId(tenantId);
			cvIds.add(cvService.createOne(cv).getId());
		}
		var jobs = new ArrayList<JobPostDTO>();
		for (var job : fixtures(segment, "job-posts.json", new TypeReference<List<JobPostDTO>>() {})) {
			job.setId(null);
			job.setTenantId(tenantId);
			jobs.add(jobPostService.createOne(job));
		}

		var reportIds = new ArrayList<String>();
		double[] scores = {82.0, 64.0, 41.0};
		for (int i = 0; i < scores.length; i++) {
			var cv = cvService.findOneById(cvIds.get(i));
			reportIds.add(matchingReportService.createOne(jobs.getFirst(), reportDetails(scores[i]), cv).getId());
		}

		var plan = productProperties.getPro();
		usageMonitoringService.initializePeriod(tenantId, plan.getStripeProductName(),
			BASE_TIME.minus(Duration.ofDays(10)), BASE_TIME.plus(Duration.ofDays(20)), plan.getFeatures());

		var noteId = insertNote(tenantId, cvIds.getFirst(), ownerEmail);
		var templateId = insertTemplate(tenantId, ownerEmail);
		var chatId = insertChat(tenantId, ownerId, cvIds.getFirst(), jobs.getFirst().getId(), reportIds.getFirst());
		var conversationId = insertConversation(tenantId, ownerEmail);

		return new SeededTenant(tenantId, name, ownerId, ownerEmail, viewerId, viewerEmail,
			List.copyOf(cvIds), jobs.stream().map(JobPostDTO::getId).toList(), List.copyOf(reportIds),
			noteId, templateId, chatId, conversationId);
	}

	private String insertTenant(String name) {
		var subscription = new SubscriptionInfo();
		subscription.setSubscriptionPlan(productProperties.getPro().getStripeProductName());
		subscription.setSubscriptionStatus("active");
		subscription.setBillingCycle("month");
		subscription.setPriceId("price_test_pro");
		subscription.setPlanCode("price_test_pro");
		subscription.setSubscriptionId("sub_test_" + name.hashCode());
		subscription.setCurrentPeriodStart(BASE_TIME.minus(Duration.ofDays(10)));
		subscription.setCurrentPeriodEnd(BASE_TIME.plus(Duration.ofDays(20)));
		subscription.setCancelAtPeriodEnd(false);

		var tenant = new Tenant();
		tenant.setTenantName(name);
		tenant.setRecruitmentType("AGENCY");
		tenant.setOrganizationSize("11-50");
		tenant.setContactEmail("contact@" + name.toLowerCase().replace(' ', '-') + ".test");
		tenant.setWebsiteUrl("https://" + name.toLowerCase().replace(' ', '-') + ".test");
		tenant.setStripeCustomerId("cus_test_" + Math.abs(name.hashCode()));
		tenant.setSubscriptionInfo(subscription);
		return mongo.insert(tenant).getId();
	}

	private String insertUser(String tenantId, String first, String last, String email, List<UserAuthority> authorities) {
		var user = new User();
		user.setTenantId(tenantId);
		user.setFirstName(first);
		user.setLastName(last);
		user.setEmail(email);
		user.setEncryptedPassword(passwordEncoder.encode(PASSWORD));
		user.setUserAccountStatus("ACTIVE");
		user.setCommunicationLanguage("en");
		user.setAuthorities(authorities);
		return mongo.insert(user).getId();
	}

	/** Read-only teammate: can browse CVs, jobs and reports, nothing else. */
	private static List<UserAuthority> viewerAuthorities() {
		return UserAuthoritiesHelper.createAuthorities().stream()
			.filter(a -> Set.of("VIEW_DASHBOARD", "VIEW_CV", "VIEW_JOB", "VIEW_REPORT").contains(a.getAction()))
			.toList();
	}

	private String insertNote(String tenantId, String cvId, String authorEmail) {
		var note = new Note();
		note.setTenantId(tenantId);
		note.setTargetType("CV");
		note.setTargetId(cvId);
		note.setText("Strong on distributed systems; ask about on-call experience.");
		note.setAuthorName("Olivia Owner");
		note.setAuthorEmail(authorEmail);
		return mongo.insert(note).getId();
	}

	private String insertTemplate(String tenantId, String author) {
		var template = new CandidateEmailTemplate();
		template.setTenantId(tenantId);
		template.setName("Quarterly availability check");
		template.setSubject("Is your profile still up to date, {{candidate_name}}?");
		template.setBodyText("Hello {{candidate_name}},\n\nCould you confirm your availability?\n\n{{company_name}}");
		template.setCreatedBy(author);
		template.setCreatedAt(BASE_TIME);
		template.setUpdatedAt(BASE_TIME);
		return mongo.insert(template).getId();
	}

	private String insertChat(String tenantId, String ownerId, String cvId, String jobId, String reportId) {
		var chat = new Chat();
		chat.setTenantId(tenantId);
		chat.setTitle("Screening follow-up");
		chat.setStatus(ChatStatus.OPEN);
		chat.setContext(ChatContext.builder().cvId(cvId).jobPostId(jobId).matchingReportId(reportId).build());
		chat.setParticipants(List.of(Participant.builder().userId(ownerId).role(Participant.Role.OWNER).build()));
		chat.setMetadata(ChatMetadata.builder().language("en").tags(List.of()).build());
		var chatId = mongo.insert(chat).getId();

		for (var turn : List.of(
			Map.entry(ChatUserRole.USER, "What are this candidate's strongest skills for the role?"),
			Map.entry(ChatUserRole.ASSISTANT, "Kubernetes, Go and incident response stand out."))) {
			var message = new ChatMessage();
			message.setTenantId(tenantId);
			message.setChatId(chatId);
			message.setRole(turn.getKey());
			message.setParticipantId(turn.getKey() == ChatUserRole.USER ? ownerId : null);
			message.setContent(turn.getValue());
			mongo.insert(message);
		}
		return chatId;
	}

	private String insertConversation(String tenantId, String askedBy) {
		var conversationId = new ObjectId().toHexString();
		var turn = new InsightConversationTurn();
		turn.setConversationId(conversationId);
		turn.setTitle("Senior backend engineers");
		turn.setTenantId(tenantId);
		turn.setInitiatedBy(askedBy);
		turn.setQuestion("How many senior backend engineers do we have?");
		turn.setEnglishQuestion("How many senior backend engineers do we have?");
		turn.setIntent(InsightIntent.TALENT_POOL_INTELLIGENCE);
		turn.setResponse(new InsightResponseDTO(conversationId, InsightIntent.TALENT_POOL_INTELLIGENCE,
			"You have 4 senior backend engineers.", List.of(), 4, List.of(), List.of(),
			List.of("Which of them are available now?"), null, Map.of()));
		mongo.insert(turn);
		return conversationId;
	}

	private static MatchingReportDetails reportDetails(double score) {
		var details = new MatchingReportDetails();
		var decision = new DecisionSummary();
		decision.setReportHeadline(score >= 70 ? "Strong match" : score >= 50 ? "Partial match" : "Weak match");
		decision.setFinalScore(score);
		decision.setShortVerdict(score >= 70 ? "Recommend interview" : "Hold");
		decision.setRecommendation(score >= 70 ? "interview" : score >= 50 ? "may_be" : "reject");
		decision.setConfidenceLevel("high");
		details.setDecisionSummary(decision);
		var skills = new SkillsMatch();
		skills.setScore(score);
		skills.setScoreSummary("Core stack overlap");
		skills.setMatchingSkills(List.of("Java", "Kubernetes"));
		details.setSkillsMatch(skills);
		return details;
	}

	private <T> List<T> fixtures(String segment, String file, TypeReference<List<T>> type) throws Exception {
		var raw = new ClassPathResource("fixtures/" + segment + "/" + file).getContentAsString(StandardCharsets.UTF_8);
		return objectMapper.readValue(DemoFixtureTokens.resolve(raw), type);
	}

	/**
	 * Rewrites every audit timestamp in insertion order (ObjectId order), one minute apart from
	 * {@link #BASE_TIME}, so "newest first" lists and response bodies are the same on every run.
	 */
	private void pinTimestamps() {
		for (var name : mongo.getCollectionNames()) {
			if (KEPT_COLLECTIONS.contains(name) || name.startsWith("system.")) continue;
			var collection = mongo.getCollection(name);
			int i = 0;
			for (var doc : collection.find().sort(new Document("_id", 1))) {
				var at = java.util.Date.from(BASE_TIME.plus(Duration.ofMinutes(i++)));
				var set = new Document();
				for (var field : List.of("createdAt", "lastUpdatedAt", "updatedAt")) {
					if (doc.containsKey(field)) set.append(field, at);
				}
				if (!set.isEmpty()) {
					collection.updateOne(new Document("_id", doc.get("_id")), new Document("$set", set));
				}
			}
		}
	}
}
