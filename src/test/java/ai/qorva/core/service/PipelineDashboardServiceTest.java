package ai.qorva.core.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineDashboardServiceTest {

	@Test
	void theMedianOfAnOddAndAnEvenNumberOfValues() {
		assertThat(PipelineDashboardService.median(List.of())).isNull();
		assertThat(PipelineDashboardService.median(List.of(5.0, 1.0, 3.0))).isEqualTo(3.0);
		assertThat(PipelineDashboardService.median(List.of(4.0, 1.0, 2.0, 10.0))).isEqualTo(3.0);
	}

	@Test
	void everyCopilotRunCountsAsOneRecruiter() {
		assertThat(PipelineDashboardService.recruiterKey("copilot:run-1")).isEqualTo("COPILOT");
		assertThat(PipelineDashboardService.recruiterKey("copilot:run-2")).isEqualTo("COPILOT");
		assertThat(PipelineDashboardService.recruiterKey("user-1")).isEqualTo("user-1");
	}
}
