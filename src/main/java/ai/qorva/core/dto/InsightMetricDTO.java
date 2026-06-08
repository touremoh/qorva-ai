package ai.qorva.core.dto;

public record InsightMetricDTO(String label, String key, String value, String unit, String percentage) {
    public InsightMetricDTO(String label, String value, String unit) {
        this(label, null, value, unit, null);
    }
    public InsightMetricDTO(String label, String key, String value, String unit) {
        this(label, key, value, unit, null);
    }
}
