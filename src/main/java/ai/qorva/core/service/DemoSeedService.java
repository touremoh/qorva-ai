package ai.qorva.core.service;

import ai.qorva.core.config.DemoSeedProperties;
import ai.qorva.core.config.S3Properties;
import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.enums.RecruitmentTypeEnum;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Seeds a freshly created demo tenant with sample CVs and job posts pulled from S3.
 * <p>
 * Fixtures carry no embeddings — MongoDB Atlas auto-generates them on insert. Idempotent: skips
 * a tenant that already has CVs. Missing (segment, language) fixture sets fall back to the
 * configured fallback language. All failures are logged and swallowed so seeding never rolls back
 * account creation.
 * <p>
 * Fixture JSON may carry relative date tokens resolved at seed time, keeping the demo data
 * evergreen (see docs/demo-seed-fixtures.md): {@code @M-n@} → month n months ago as "yyyy-MM",
 * {@code @Y-n@} → year n years ago as "yyyy".
 */
@Slf4j
@Service
public class DemoSeedService {

	private final S3Client s3Client;
	private final S3Properties s3Properties;
	private final DemoSeedProperties demoSeedProperties;
	private final ObjectMapper objectMapper;
	private final CVService cvService;
	private final JobPostService jobPostService;

	@Autowired
	public DemoSeedService(
		S3Client s3Client,
		S3Properties s3Properties,
		DemoSeedProperties demoSeedProperties,
		ObjectMapper objectMapper,
		CVService cvService,
		JobPostService jobPostService
	) {
		this.s3Client = s3Client;
		this.s3Properties = s3Properties;
		this.demoSeedProperties = demoSeedProperties;
		this.objectMapper = objectMapper;
		this.cvService = cvService;
		this.jobPostService = jobPostService;
	}

	/**
	 * Seeds sample data for the tenant. Best-effort — never throws.
	 *
	 * @param tenantId        the demo tenant to populate
	 * @param recruitmentType the segment value (see {@link RecruitmentTypeEnum}); GENERALIST is used if unknown
	 * @param languageCode    the tenant's language (base code, e.g. "en")
	 */
	public void seed(String tenantId, String recruitmentType, String languageCode) {
		if (!demoSeedProperties.isEnabled()) {
			log.info("Demo seeding disabled – skipping tenant={}", tenantId);
			return;
		}
		try {
			// Idempotency: skip if the tenant already has CVs
			if (cvService.countAll(tenantId) > 0) {
				log.info("Demo seeding skipped – tenant={} already has CVs", tenantId);
				return;
			}

			var slug = RecruitmentTypeEnum.fromValue(recruitmentType)
				.orElse(RecruitmentTypeEnum.GENERALIST)
				.getSlug();
			var lang = normalizeLang(languageCode);

			var cvs = fetchFixtures(slug, lang, "cvs.json", new TypeReference<List<CVDTO>>() {});
			var jobPosts = fetchFixtures(slug, lang, "job-posts.json", new TypeReference<List<JobPostDTO>>() {});

			int seededCvs = 0;
			for (var cv : cvs) {
				try {
					cv.setId(null);
					cv.setTenantId(tenantId);
					cvService.createOne(cv);
					seededCvs++;
				} catch (Exception e) {
					log.warn("Failed to seed a sample CV for tenant={}", tenantId, e);
				}
			}

			int seededJobs = 0;
			for (var job : jobPosts) {
				try {
					job.setId(null);
					job.setTenantId(tenantId);
					jobPostService.createOne(job);
					seededJobs++;
				} catch (Exception e) {
					log.warn("Failed to seed a sample job post for tenant={}", tenantId, e);
				}
			}

			log.info("Demo seeding complete for tenant={} segment={} lang={}: {} CVs, {} job posts",
				tenantId, slug, lang, seededCvs, seededJobs);
		} catch (Exception e) {
			log.error("Demo seeding failed for tenant={} – account left without sample data", tenantId, e);
		}
	}

	private <T> List<T> fetchFixtures(String slug, String lang, String fileName, TypeReference<List<T>> type) {
		var bytes = fetchObject(buildKey(slug, lang, fileName));
		if (bytes == null && !lang.equals(demoSeedProperties.getFallbackLanguage())) {
			log.info("Fixture {} missing for segment={} lang={} – falling back to {}",
				fileName, slug, lang, demoSeedProperties.getFallbackLanguage());
			bytes = fetchObject(buildKey(slug, demoSeedProperties.getFallbackLanguage(), fileName));
		}
		if (bytes == null) {
			log.warn("No fixture {} found for segment={} (lang={} or fallback)", fileName, slug, lang);
			return List.of();
		}
		try {
			var json = resolveDateTokens(new String(bytes, StandardCharsets.UTF_8));
			return objectMapper.readValue(json, type);
		} catch (Exception e) {
			log.error("Failed to parse fixture {} for segment={}", fileName, slug, e);
			return List.of();
		}
	}

	private static final Pattern MONTHS_AGO_TOKEN = Pattern.compile("@M-(\\d{1,3})@");
	private static final Pattern YEARS_AGO_TOKEN = Pattern.compile("@Y-(\\d{1,2})@");

	static String resolveDateTokens(String rawJson) {
		return resolveDateTokens(rawJson, YearMonth.now(ZoneOffset.UTC));
	}

	/** Replaces relative date tokens so fixture freshness never decays as the fixtures age. */
	static String resolveDateTokens(String rawJson, YearMonth now) {
		var withMonths = MONTHS_AGO_TOKEN.matcher(rawJson)
			.replaceAll(match -> now.minusMonths(Long.parseLong(match.group(1))).toString());
		return YEARS_AGO_TOKEN.matcher(withMonths)
			.replaceAll(match -> String.valueOf(now.getYear() - Integer.parseInt(match.group(1))));
	}

	private byte[] fetchObject(String key) {
		try {
			return s3Client.getObjectAsBytes(
				GetObjectRequest.builder().bucket(resolveBucket()).key(key).build()
			).asByteArray();
		} catch (NoSuchKeyException e) {
			return null;
		} catch (Exception e) {
			log.warn("S3 fetch failed for key={}", key, e);
			return null;
		}
	}

	private String buildKey(String slug, String lang, String fileName) {
		return demoSeedProperties.getPrefix() + "/" + demoSeedProperties.getVersion()
			+ "/" + slug + "/" + lang + "/" + fileName;
	}

	private String resolveBucket() {
		return StringUtils.hasText(demoSeedProperties.getBucket())
			? demoSeedProperties.getBucket()
			: s3Properties.getBucketName();
	}

	private String normalizeLang(String languageCode) {
		if (!StringUtils.hasText(languageCode)) {
			return demoSeedProperties.getFallbackLanguage();
		}
		// Reduce e.g. "en-US" to base code "en"
		return languageCode.split("[-_]")[0].toLowerCase();
	}
}
