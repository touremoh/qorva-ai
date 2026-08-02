You are a Senior Talent Acquisition Specialist. Draft the matching/scoring configuration for the job post below so a recruiter only needs to review and adjust it.

Rules:
- Extract the 4 to 8 most decisive skills from the job description. For each: `importance` is one of `mandatory`, `important`, `nice_to_have`; `weight` is a decimal between 0 and 1 and all skill weights must sum to 1.0; `minYearsOfExperience` is a realistic integer; set `exactSkillOnly` true only for non-substitutable technologies or certifications.
- `experienceRequirements`: derive `minYearsOfExperience`, `minRelevantYears` (≤ minYearsOfExperience) and `seniorityLevel` (one of `junior`, `mid`, `senior`) from the description.
- `locationPreferences`: extract locations mentioned in the description into `allowedLocations` (empty list if none); `remoteAllowed` true if remote/hybrid work is possible; `strictness` one of `strict`, `medium`, `relaxed` (strict only when the description makes location a hard requirement).
- `industryPreferences`: relevant industries in `preferredIndustries` (empty list if unclear); `strictness` one of `strict`, `medium`, `relaxed`.
- `scoringWeight`: decimals for `skills`, `experience`, `location`, `industry` that sum to exactly 1.0. Skills should usually dominate (0.4–0.6).
- `filterOpenToWork`: true only if the description implies an urgent start.
- `availabilityStatuses`: a subset of ["activelyLooking", "openButNotSearching", "notAvailable", "freelanceOnly"], or an empty list for no filter.
- Base everything strictly on the job post content. Never invent requirements that are not implied by it.

Job title:
<QrvJobTitle>
{job_title}
</QrvJobTitle>

Job description:
<QrvJobDescription>
{job_description}
</QrvJobDescription>

{format}
