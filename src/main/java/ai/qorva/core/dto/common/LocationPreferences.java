package ai.qorva.core.dto.common;

import java.util.List;

public record LocationPreferences(
        List<String> allowedLocations,
        Boolean remoteAllowed,
        String strictness
) {}
