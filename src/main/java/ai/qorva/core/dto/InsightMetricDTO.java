package ai.qorva.core.dto;

public record InsightMetricDTO(String label, String key, String value, String unit) {
    public InsightMetricDTO(String label, String value, String unit) {
        this(label, null, value, unit);
    }
}
