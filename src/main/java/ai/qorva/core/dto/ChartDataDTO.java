package ai.qorva.core.dto;

import java.util.List;

public record ChartDataDTO(
        String chartType,
        String title,
        List<String> labels,
        List<Number> values
) {}
