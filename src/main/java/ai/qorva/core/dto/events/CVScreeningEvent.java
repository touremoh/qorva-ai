package ai.qorva.core.dto.events;

import ai.qorva.core.dto.JobPostDTO;

public record CVScreeningEvent(JobPostDTO jobPost) {
}
