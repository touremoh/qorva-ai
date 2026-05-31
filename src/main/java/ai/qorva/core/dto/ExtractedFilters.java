package ai.qorva.core.dto;

import java.util.List;

public record ExtractedFilters(
        List<String> skills,
        List<String> roles,
        String seniority,         // junior | midLevel | senior | lead | principal | manager | director | executive
        String skillDepth,        // generalist | specialist | tShaped | hybrid
        String leadershipLevel,   // individualContributor | teamLead | crossFunctionalLeader | strategicLeader | executiveInfluence
        String location,
        List<String> industries,
        Integer minYearsExperience,
        List<String> tags,        // frontend-managed; always [] from entity extractor
        Integer limit             // max candidates to return; null = system default (10)
) {
    public static ExtractedFilters empty() {
        return new ExtractedFilters(List.of(), List.of(), null, null, null, null, List.of(), null, List.of(), null);
    }
}
