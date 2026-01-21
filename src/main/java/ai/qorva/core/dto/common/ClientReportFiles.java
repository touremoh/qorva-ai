package ai.qorva.core.dto.common;

import ai.qorva.core.enums.ClientReportStorageProvider;

public record ClientReportFiles(
        String pdfUrl,
        String htmlUrl,
        ClientReportStorageProvider storageProvider,
        String sha256,
        Long fileSizeBytes
) {}