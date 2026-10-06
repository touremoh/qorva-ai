package ai.qorva.core.it;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.config.QorvaProductProperties;
import ai.qorva.core.dao.entity.CandidateEmailTemplate;
import ai.qorva.core.dao.entity.AgentRun;
import ai.qorva.core.dao.entity.Note;
import ai.qorva.core.dao.entity.Tenant;
import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.AnswerBlocks;
import ai.qorva.core.dto.CVQueryParams;
import ai.qorva.core.dto.CandidateCardDTO;
import ai.qorva.core.dto.ChartDataDTO;
import ai.qorva.core.dto.ConversationFrame;
import ai.qorva.core.dto.InsightIntent;
import ai.qorva.core.dto.InsightMetricDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.TenantDTO;
import ai.qorva.core.dto.common.DecisionSummary;
import ai.qorva.core.dto.common.MatchingReportDetails;
import ai.qorva.core.dto.common.SkillsMatch;
import ai.qorva.core.dto.common.SubscriptionInfo;
import ai.qorva.core.dto.common.UserAuthority;
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
		String noteId, String templateId,
		String agentRunId, String agentConversationId, String candidateAnswerRunId, String libraryAnswerRunId
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
			periodStart(), periodEnd(), plan.getFeatures());

		var noteId = insertNote(tenantId, cvIds.getFirst(), ownerEmail);
		var templateId = insertTemplate(tenantId, ownerEmail);
		var agentRun = insertAgentRun(tenantId, ownerEmail, cvIds.getFirst());
		var candidateRun = insertCandidateAnswerRun(agentRun, cvIds.getFirst(), jobs.getFirst(), reportIds.getFirst());
		var libraryRun = insertLibraryAnswerRun(agentRun, cvIds.getFirst());

		return new SeededTenant(tenantId, name, ownerId, ownerEmail, viewerId, viewerEmail,
			List.copyOf(cvIds), jobs.stream().map(JobPostDTO::getId).toList(), List.copyOf(reportIds),
			noteId, templateId, agentRun.getId(), agentRun.getConversationId(), candidateRun.getId(), libraryRun.getId());
	}

	private String insertTenant(String name) {
		var subscription = new SubscriptionInfo();
		subscription.setSubscriptionPlan(productProperties.getPro().getStripeProductName());
		subscription.setSubscriptionStatus("active");
		subscription.setBillingCycle("month");
		subscription.setPriceId("price_test_pro");
		subscription.setPlanCode("price_test_pro");
		subscription.setSubscriptionId("sub_test_" + name.hashCode());
		subscription.setCurrentPeriodStart(periodStart());
		subscription.setCurrentPeriodEnd(periodEnd());
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

	private AgentRun insertAgentRun(String tenantId, String userEmail, String cvId) {
		var run = new AgentRun();
		run.setTenantId(tenantId);
		run.setUserEmail(userEmail);
		run.setConversationId(new ObjectId().toHexString());
		run.setTitle("Best Java candidates");
		run.setOrigin(AgentRun.ORIGIN_CHAT);
		run.setLanguage("en");
		run.setGoal("Who are our best Java candidates?");
		run.setStatus(AgentRun.STATUS_COMPLETED);
		var step = new AgentRun.Step();
		step.setSeq(1);
		step.setKind(AgentRun.Step.KIND_TOOL_CALL);
		step.setTool("search_cvs");
		step.setState(AgentRun.Step.STATE_OK);
		step.setSummaryKey("agent.step.search_cvs");
		step.setSummaryParams(new java.util.LinkedHashMap<>(Map.of("count", "1")));
		step.setLinks(new ArrayList<>(List.of(new AgentRun.Link("CV", cvId, "Candidate"))));
		run.getSteps().add(step);
		run.setFinalAnswer("One strong Java candidate.");
		run.setStepCount(2);
		run.setToolCallCount(1);
		run.setMetered(true);
		run.setCreatedAt(BASE_TIME);
		run.setFinishedAt(BASE_TIME);
		return mongo.insert(run);
	}

	/** A follow-up in the same conversation, focused on a candidate for a job and answered by ask_about_candidate. */
	private AgentRun insertCandidateAnswerRun(AgentRun first, String cvId, JobPostDTO job, String reportId) {
		var run = followUp(first, "What are this candidate's strongest skills for the role?");
		run.setFocus(new AgentRun.Focus(cvId, "Candidate", job.getId(), job.getTitle()));
		run.getSteps().add(answerStep("ask_about_candidate", "agent.step.ask_about_candidate",
			Map.of("name", "Candidate", "job", job.getTitle()), List.of(new AgentRun.Link("CV", cvId, "Candidate"),
				new AgentRun.Link("JOB", job.getId(), job.getTitle()), new AgentRun.Link("REPORT", reportId, job.getTitle()))));
		run.setFinalAnswer("Kubernetes, Go and incident response stand out.");
		return mongo.insert(run);
	}

	/** A library question in the same conversation, answered by analyze_library with its blocks and frame. */
	private AgentRun insertLibraryAnswerRun(AgentRun first, String cvId) {
		var run = followUp(first, "How many senior backend engineers do we have?");
		run.getSteps().add(answerStep("analyze_library", "agent.step.analyze_library",
			Map.of("intent", InsightIntent.TALENT_POOL_INTELLIGENCE.name()), List.of()));
		run.setFinalAnswer("You have 4 senior backend engineers.");
		run.setBlocks(new AnswerBlocks(InsightIntent.TALENT_POOL_INTELLIGENCE,
			List.of(new CandidateCardDTO(cvId, "A-1", "Candidate", "Backend engineer", List.of("Java", "Kubernetes"), "senior", null, "Paris")),
			4, List.of(new InsightMetricDTO("Senior backend engineers", "count", "4", null)),
			List.of(new ChartDataDTO("bar", "Seniority", List.of("senior", "mid"), List.of(4, 6))),
			List.of("Which of them are available now?"), null, null));
		run.setInsightFrame(new ConversationFrame("How many senior backend engineers do we have?",
			InsightIntent.TALENT_POOL_INTELLIGENCE, CVQueryParams.empty(), false, BASE_TIME));
		return mongo.insert(run);
	}

	private static AgentRun followUp(AgentRun first, String goal) {
		var run = new AgentRun();
		run.setTenantId(first.getTenantId());
		run.setUserEmail(first.getUserEmail());
		run.setConversationId(first.getConversationId());
		run.setTitle(first.getTitle());
		run.setOrigin(AgentRun.ORIGIN_CHAT);
		run.setLanguage("en");
		run.setGoal(goal);
		run.setStatus(AgentRun.STATUS_COMPLETED);
		run.setStepCount(1);
		run.setToolCallCount(1);
		run.setCreatedAt(BASE_TIME);
		run.setFinishedAt(BASE_TIME);
		return run;
	}

	private static AgentRun.Step answerStep(String tool, String summaryKey, Map<String, String> params, List<AgentRun.Link> links) {
		var step = new AgentRun.Step();
		step.setSeq(1);
		step.setKind(AgentRun.Step.KIND_TOOL_CALL);
		step.setTool(tool);
		step.setTier("READ");
		step.setState(AgentRun.Step.STATE_OK);
		step.setSummaryKey(summaryKey);
		step.setSummaryParams(new java.util.LinkedHashMap<>(params));
		step.setLinks(new ArrayList<>(links));
		return step;
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
	/*
	 * The billing period is looked up against the real clock (UsageMonitoringService.findCurrentPeriodByTenantId:
	 * start <= now < end), so it is anchored on now, not on BASE_TIME — a fixed window expired on
	 * 2026-10-05T08:00Z and took every quota test with it. Contract goldens normalise these dates to <ts>.
	 */
	private static Instant periodStart() {
		return Instant.now().minus(Duration.ofDays(10)).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
	}

	private static Instant periodEnd() {
		return Instant.now().plus(Duration.ofDays(20)).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
	}

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
