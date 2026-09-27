package ai.qorva.core.dto;

import java.util.List;

/** The user-facing texts of an AI summary, as sent to and returned by the translation call. */
public record InsightTexts(String headline, String explanation, List<String> recommendations) {}
