package ai.qorva.core.service;

import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.ScoringRules;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A job edit makes its matching results stale only when what the reports are computed from changes (title,
 * description, scoring rules) or the job reopens — and never downgrades a stronger stale reason.
 */
class JobPostMatchingStateTest {

	private static JobPostDTO matchedJob() {
		var job = new JobPostDTO();
		job.setTitle("Backend Lead");
		job.setDescription("<p>Java</p>");
		job.setStatus("open");
		job.setScoringRules(new ScoringRules());
		job.setMatchingReportsNeeded(false);
		job.setLastMatchedAt(Instant.parse("2026-10-01T10:00:00Z"));
		return job;
	}

	/** The update as it looks after the merge: a copy of what is stored, with the client's changes. */
	private static JobPostDTO edited(JobPostDTO existing) {
		var job = new JobPostDTO();
		job.setTitle(existing.getTitle());
		job.setDescription(existing.getDescription());
		job.setStatus(existing.getStatus());
		job.setScoringRules(existing.getScoringRules());
		job.setMatchingReportsNeeded(existing.getMatchingReportsNeeded());
		job.setMatchingStaleReason(existing.getMatchingStaleReason());
		job.setLastMatchedAt(existing.getLastMatchedAt());
		return job;
	}

	@Test
	void aDescriptionChangeMakesTheResultsStale() {
		var existing = matchedJob();
		var updated = edited(existing);
		updated.setDescription("<p>Java and Kotlin</p>");

		JobPostService.applyMatchingState(existing, updated);

		assertThat(updated.getMatchingReportsNeeded()).isTrue();
		assertThat(updated.getMatchingStaleReason()).isEqualTo("JOB_CHANGED");
	}

	@Test
	void anEditThatLeavesTheMatchingInputAloneKeepsTheResultsCurrent() {
		var existing = matchedJob();
		var updated = edited(existing);
		updated.setMatchingReportsNeeded(true); // a client cannot set the flag

		JobPostService.applyMatchingState(existing, updated);

		assertThat(updated.getMatchingReportsNeeded()).isFalse();
		assertThat(updated.getMatchingStaleReason()).isNull();
	}

	@Test
	void closingClearsTheFlagAndReopeningSetsIt() {
		var existing = matchedJob();
		var closed = edited(existing);
		closed.setStatus("closed");
		JobPostService.applyMatchingState(existing, closed);
		assertThat(closed.getMatchingReportsNeeded()).isFalse();

		var reopened = edited(closed);
		reopened.setStatus("open");
		JobPostService.applyMatchingState(closed, reopened);
		assertThat(reopened.getMatchingReportsNeeded()).isTrue();
		assertThat(reopened.getMatchingStaleReason()).isEqualTo("JOB_CHANGED");
	}

	@Test
	void aNeverMatchedJobStaysNeverRun() {
		var existing = matchedJob();
		existing.setLastMatchedAt(null);
		existing.setMatchingStaleReason("NEVER_RUN");
		var updated = edited(existing);
		updated.setTitle("Staff Backend Lead");

		JobPostService.applyMatchingState(existing, updated);

		assertThat(updated.getMatchingStaleReason()).isEqualTo("NEVER_RUN");
	}

	@Test
	void becomingStaleOrStalerStartsAnEpisodeButTheSameReasonAgainDoesNot() {
		var current = matchedJob();
		var changed = edited(current);
		changed.setTitle("Staff Backend Lead");
		JobPostService.applyMatchingState(current, changed);
		assertThat(changed.getMatchingStaleAt()).isNotNull();

		var started = Instant.parse("2026-10-03T09:00:00Z");
		var stale = matchedJob();
		stale.setMatchingReportsNeeded(true);
		stale.setMatchingStaleReason("JOB_CHANGED");
		stale.setMatchingStaleAt(started);
		var again = edited(stale);
		again.setMatchingStaleAt(started);
		again.setDescription("<p>Java and Go</p>");
		JobPostService.applyMatchingState(stale, again);
		assertThat(again.getMatchingStaleAt()).isEqualTo(started);

		var weaker = matchedJob();
		weaker.setMatchingReportsNeeded(true);
		weaker.setMatchingStaleReason("NEW_CANDIDATES");
		weaker.setMatchingStaleAt(started);
		var stronger = edited(weaker);
		stronger.setMatchingStaleAt(started);
		stronger.setTitle("Principal Backend Lead");
		JobPostService.applyMatchingState(weaker, stronger);
		assertThat(stronger.getMatchingStaleAt()).isAfter(started);
	}

	@Test
	void aContentChangeOutranksNewCandidates() {
		var existing = matchedJob();
		existing.setMatchingReportsNeeded(true);
		existing.setMatchingStaleReason("NEW_CANDIDATES");
		var updated = edited(existing);
		updated.setTitle("Staff Backend Lead");

		JobPostService.applyMatchingState(existing, updated);

		assertThat(updated.getMatchingStaleReason()).isEqualTo("JOB_CHANGED");
	}
}
