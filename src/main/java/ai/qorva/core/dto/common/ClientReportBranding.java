package ai.qorva.core.dto.common;

public record ClientReportBranding(
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String accentColor,
        String footerText
) {}