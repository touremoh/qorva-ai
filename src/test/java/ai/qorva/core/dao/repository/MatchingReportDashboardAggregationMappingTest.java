package ai.qorva.core.dao.repository;

import ai.qorva.core.dao.entity.MatchingReport;
import ai.qorva.core.dto.DashboardData;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.aggregation.TypeBasedAggregationOperationContext;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.Aggregation;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Spring Data maps every stage of a string {@code @Aggregation} against the repository's entity
 * before sending it, so an output field that shares a name with an entity field (e.g. the
 * OBJECT_ID-typed {@code jobPostId}) gets its expression coerced to that type. No Mongo needed:
 * this renders the stages through the same mapping {@code StringBasedAggregation} uses.
 */
class MatchingReportDashboardAggregationMappingTest {

	private TypeBasedAggregationOperationContext context;
	private MappingMongoConverter converter;

	@BeforeEach
	void setUp() {
		var conversions = new MongoCustomConversions(List.of());
		var mappingContext = new MongoMappingContext();
		mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
		mappingContext.setAutoIndexCreation(false);
		converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
		converter.setCustomConversions(conversions);
		converter.afterPropertiesSet();
		context = new TypeBasedAggregationOperationContext(MatchingReport.class, mappingContext, new QueryMapper(converter));
	}

	@ParameterizedTest
	@ValueSource(strings = {"getApplicationsPerJobPost", "getTopCandidatesPerJobPost", "countDistinctJobPosts"})
	void dashboardPipelinesSurviveEntityMapping(String methodName) {
		var method = Arrays.stream(MatchingReportRepository.class.getMethods())
			.filter(m -> m.getName().equals(methodName))
			.findFirst()
			.orElseThrow();
		var stages = method.getAnnotation(Aggregation.class).pipeline();
		assertThat(stages).isNotEmpty();

		for (var stage : stages) {
			var json = stage.replaceAll("\\?\\d+", "{ '\\$oid': '0123456789abcdef01234567' }");
			assertThatCode(() -> context.getMappedObject(Document.parse(json)))
				.as("stage %s", stage)
				.doesNotThrowAnyException();
		}
	}

	@Test
	void groupKeyIsReadIntoJobPostId() {
		var jobPostId = new ObjectId();

		var report = converter.read(DashboardData.ApplicationPerJobPostReport.class,
			new Document("_id", jobPostId).append("jobPostTitle", "Java Developer").append("totalMatch", 3));
		assertThat(report.jobPostId()).isEqualTo(jobPostId.toHexString());
		assertThat(report.totalMatch()).isEqualTo(3);

		var top = converter.read(DashboardData.TopCandidatesPerJobReport.class,
			new Document("_id", jobPostId).append("jobPostTitle", "Java Developer")
				.append("topCandidates", List.of(new Document("candidateId", "c1").append("candidateName", "Ada").append("score", 91))));
		assertThat(top.jobPostId()).isEqualTo(jobPostId.toHexString());
		assertThat(top.topCandidates()).extracting(DashboardData.TopCandidate::score).containsExactly(91);
	}
}
