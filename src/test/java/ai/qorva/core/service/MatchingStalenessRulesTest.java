package ai.qorva.core.service;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.Availability;
import ai.qorva.core.dto.common.PersonalInformation;
import ai.qorva.core.dto.common.ScoringRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The per-candidate decision of smart flagging: same similarity scale as Atlas, same eligibility as the search. */
class MatchingStalenessRulesTest {

	private static JobPostDTO job(float[] embedding, Double cutoff) {
		var job = new JobPostDTO();
		job.setEmbedding(embedding);
		job.setMatchingCutoffScore(cutoff);
		return job;
	}

	private static CVDTO cv(float[] embedding) {
		var cv = new CVDTO();
		cv.setEmbedding(embedding);
		var availability = new Availability();
		availability.setOpenToWork(true);
		availability.setStatus("activelyLooking");
		var info = new PersonalInformation();
		info.setAvailability(availability);
		cv.setPersonalInformation(info);
		return cv;
	}

	@Test
	void similarityIsCosineMappedToZeroOneLikeAtlas() {
		assertThat(MatchingStalenessService.similarity(new float[] {1, 0}, new float[] {1, 0})).isCloseTo(1.0, within(1e-9));
		assertThat(MatchingStalenessService.similarity(new float[] {1, 0}, new float[] {0, 1})).isCloseTo(0.5, within(1e-9));
		assertThat(MatchingStalenessService.similarity(new float[] {1, 0}, new float[] {-1, 0})).isCloseTo(0.0, within(1e-9));
		assertThat(MatchingStalenessService.similarity(new float[] {1, 0}, new float[] {1, 0, 0})).isNull();
		assertThat(MatchingStalenessService.similarity(null, new float[] {1})).isNull();
	}

	@Test
	void aCandidateEntersTheTopNWhenAtLeastAsSimilarAsTheCutoff() {
		var close = cv(new float[] {1, 0.1f});
		assertThat(MatchingStalenessService.wouldEnterTopN(job(new float[] {1, 0}, 0.99), close)).isTrue();
		assertThat(MatchingStalenessService.wouldEnterTopN(job(new float[] {0, 1}, 0.99), close)).isFalse();
	}

	@Test
	void withoutACutoffTheSimilarityFloorApplies() {
		var orthogonal = cv(new float[] {0, 1});
		assertThat(MatchingStalenessService.wouldEnterTopN(job(new float[] {1, 0}, null), orthogonal)).isTrue();
		assertThat(MatchingStalenessService.wouldEnterTopN(job(new float[] {1, 0}, null), cv(new float[] {-1, 0.2f}))).isFalse();
	}

	@Test
	void anIneligibleCandidateNeverFlagsTheJob() {
		var job = job(new float[] {1, 0}, 0.5);
		var archived = cv(new float[] {1, 0});
		archived.setArchived(true);
		assertThat(MatchingStalenessService.wouldEnterTopN(job, archived)).isFalse();

		var rules = new ScoringRules();
		rules.setAvailabilityStatuses(List.of("openButNotSearching"));
		job.setScoringRules(rules);
		assertThat(MatchingStalenessService.wouldEnterTopN(job, cv(new float[] {1, 0}))).isFalse();

		rules.setAvailabilityStatuses(null);
		rules.setFilterOpenToWork(true);
		var notOpen = cv(new float[] {1, 0});
		notOpen.getPersonalInformation().getAvailability().setOpenToWork(false);
		assertThat(MatchingStalenessService.wouldEnterTopN(job, notOpen)).isFalse();
	}

	@Test
	void theInputFingerprintChangesWithEveryPromptInputAndNothingElse() {
		var job = new JobPostDTO();
		job.setTitle("Backend Lead");
		job.setDescription("Java");
		var cv = new CVDTO();
		cv.setCandidateProfileSummary("Ten years of Java");
		var base = MatchingFingerprint.input(MatchingFingerprint.cv(cv), MatchingFingerprint.job(job), "en", "v1");

		cv.setTags(List.of("shortlist"));
		assertThat(MatchingFingerprint.input(MatchingFingerprint.cv(cv), MatchingFingerprint.job(job), "en", "v1")).isEqualTo(base);

		assertThat(MatchingFingerprint.input(MatchingFingerprint.cv(cv), MatchingFingerprint.job(job), "fr", "v1")).isNotEqualTo(base);
		assertThat(MatchingFingerprint.input(MatchingFingerprint.cv(cv), MatchingFingerprint.job(job), "en", "v2")).isNotEqualTo(base);
		job.setDescription("Java and Kotlin");
		assertThat(MatchingFingerprint.input(MatchingFingerprint.cv(cv), MatchingFingerprint.job(job), "en", "v1")).isNotEqualTo(base);
		job.setDescription("Java");
		cv.setCandidateProfileSummary("Eleven years of Java");
		assertThat(MatchingFingerprint.input(MatchingFingerprint.cv(cv), MatchingFingerprint.job(job), "en", "v1")).isNotEqualTo(base);
	}
}
