package ai.qorva.core.service;

import ai.qorva.core.dto.CVDTO;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.dto.common.*;
import ai.qorva.core.exception.QorvaException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ATSExportService {

    private final MatchingReportService matchingReportService;
    private final CVService cvService;

    @Autowired
    public ATSExportService(MatchingReportService matchingReportService, CVService cvService) {
        this.matchingReportService = matchingReportService;
        this.cvService = cvService;
    }

    private static final String[] GLOBAL_HEADERS = {
        "first_name", "last_name", "email", "phone", "city", "country",
        "current_title", "current_employer", "years_experience", "highest_education",
        "skills", "languages", "linkedin_url", "source",
        "qorva_ref", "qorva_match_score", "qorva_rank",
        "seniority_level", "primary_cluster", "secondary_cluster",
        "environment_fit", "mandatory_skills_met", "match_strengths", "match_gaps",
        "notes"
    };

    private static final String[] EU_EXTRA_HEADERS = {
        "tags", "notice_period_days", "cv_age_months",
        "gdpr_consent_obtained", "data_source"
    };

    private static final Map<String, Integer> DEGREE_RANK = Map.ofEntries(
        Map.entry("phd", 5), Map.entry("doctorate", 5),
        Map.entry("master", 4), Map.entry("msc", 4), Map.entry("mba", 4), Map.entry("ma", 4), Map.entry("meng", 4),
        Map.entry("bachelor", 3), Map.entry("bsc", 3), Map.entry("ba", 3), Map.entry("beng", 3), Map.entry("bs", 3),
        Map.entry("associate", 2),
        Map.entry("high school", 1), Map.entry("baccalaureate", 1), Map.entry("bac", 1)
    );

    public ResponseEntity<byte[]> exportCsv(String tenantId, String jobPostId, String format) throws QorvaException {
        boolean euFormat = "eu".equalsIgnoreCase(format);

        List<MatchingReportDTO> reports = new ArrayList<>(matchingReportService.findAllForExport(tenantId, jobPostId));
        if (reports.isEmpty()) {
            throw new QorvaException("No matching reports found for job post: " + jobPostId);
        }

        reports.sort(Comparator.comparingDouble(
            (MatchingReportDTO r) -> Optional.ofNullable(r.getMatchingReportDetails())
                .map(MatchingReportDetails::getDecisionSummary)
                .map(DecisionSummary::getFinalScore)
                .orElse(0.0)
        ).reversed());

        List<String> candidateIds = reports.stream()
            .map(r -> r.getCandidateInfo() != null ? r.getCandidateInfo().getCandidateId() : null)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

        Map<String, CVDTO> cvMap = cvService.findAllByIds(candidateIds).stream()
            .collect(Collectors.toMap(CVDTO::getId, cv -> cv));

        String jobTitle = reports.get(0).getJobPostTitle() != null ? reports.get(0).getJobPostTitle() : "export";
        String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String filename = "qorva-" + jobTitle.replaceAll("[^a-zA-Z0-9-]", "-").toLowerCase() + "-" + date + ".csv";

        String[] headers = euFormat ? concat(GLOBAL_HEADERS, EU_EXTRA_HEADERS) : GLOBAL_HEADERS;

        try (StringWriter sw = new StringWriter();
             CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setHeader(headers).build())) {

            int rank = 1;
            for (MatchingReportDTO report : reports) {
                CVDTO cv = cvMap.get(
                    report.getCandidateInfo() != null ? report.getCandidateInfo().getCandidateId() : null
                );
                printer.printRecord(buildRow(report, cv, rank++, euFormat));
            }
            printer.flush();

            byte[] bytes = sw.toString().getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);

        } catch (Exception e) {
            log.error("Error generating ATS CSV export for jobPostId={}", jobPostId, e);
            throw new QorvaException("Failed to generate CSV export: " + e.getMessage());
        }
    }

    private List<Object> buildRow(MatchingReportDTO report, CVDTO cv, int rank, boolean euFormat) {
        CandidateInfo candidateInfo = report.getCandidateInfo();
        MatchingReportDetails details = report.getMatchingReportDetails();
        DecisionSummary decision = details != null ? details.getDecisionSummary() : null;
        MissingSkills missingSkills = details != null ? details.getMissingSkills() : null;
        List<Strength> strengths = details != null && details.getStrengths() != null
            ? details.getStrengths() : List.of();

        PersonalInformation pi = cv != null ? cv.getPersonalInformation() : null;
        Contact contact = pi != null ? pi.getContact() : null;
        Address address = contact != null ? contact.getAddress() : null;
        SocialLinks socialLinks = contact != null ? contact.getSocialLinks() : null;
        CandidateClustering clustering = cv != null ? cv.getCandidateClustering() : null;
        if (clustering == null && candidateInfo != null) clustering = candidateInfo.getCandidateClustering();

        // Name split on first space
        String fullName = pi != null && pi.getName() != null
            ? pi.getName()
            : (candidateInfo != null ? nvl(candidateInfo.getCandidateName()) : "");
        int spaceIdx = fullName.indexOf(' ');
        String firstName = spaceIdx > 0 ? fullName.substring(0, spaceIdx) : fullName;
        String lastName = spaceIdx > 0 ? fullName.substring(spaceIdx + 1) : "";

        // Current employer from most recent work experience
        String currentEmployer = cv != null && !CollectionUtils.isEmpty(cv.getWorkExperience())
            ? nvl(cv.getWorkExperience().get(0).getCompany()) : "";

        // Years of experience
        Object yearsExp = cv != null && cv.getNbYearsOfExperience() != null
            ? cv.getNbYearsOfExperience()
            : (candidateInfo != null ? candidateInfo.getNbYearsExperience() : "");

        // Skills — prefer full CV data, fall back to candidateInfo snapshot
        String skills;
        if (cv != null && !CollectionUtils.isEmpty(cv.getKeySkills())) {
            skills = cv.getKeySkills().stream()
                .filter(ks -> ks.getSkills() != null)
                .flatMap(ks -> ks.getSkills().stream())
                .collect(Collectors.joining(", "));
        } else {
            List<String> fallback = candidateInfo != null ? candidateInfo.getSkills() : null;
            skills = !CollectionUtils.isEmpty(fallback) ? String.join(", ", fallback) : "";
        }

        // Languages
        String languages = "";
        if (cv != null && cv.getSkillsAndQualifications() != null
                && !CollectionUtils.isEmpty(cv.getSkillsAndQualifications().getLanguages())) {
            languages = cv.getSkillsAndQualifications().getLanguages().stream()
                .map(this::formatLanguage)
                .collect(Collectors.joining(", "));
        }

        double score = decision != null && decision.getFinalScore() != null ? decision.getFinalScore() : 0.0;
        String scoreStr = String.format("%.0f%%", score);
        String seniority = clustering != null ? humanizeSeniority(clustering.getSeniorityLevel()) : "";
        String primaryCluster = clustering != null ? nvl(clustering.getPrimaryCluster()) : "";

        String secondaryCluster = clustering != null && !CollectionUtils.isEmpty(clustering.getSecondaryClusters())
            ? clustering.getSecondaryClusters().get(0) : "";

        String envFit = clustering != null && !CollectionUtils.isEmpty(clustering.getEnvironmentFit())
            ? String.join("; ", clustering.getEnvironmentFit()) : "";

        String matchStrengths = strengths.stream()
            .map(Strength::getTitle)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("; "));

        String matchGaps = missingSkills != null && !CollectionUtils.isEmpty(missingSkills.getSkills())
            ? missingSkills.getSkills().stream()
                .map(MissingSkills.SkillEntry::getSkill)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "))
            : "";

        String envFitFirst = clustering != null && !CollectionUtils.isEmpty(clustering.getEnvironmentFit())
            ? clustering.getEnvironmentFit().get(0) : "";

        List<Object> row = new ArrayList<>(Arrays.asList(
            firstName,
            lastName,
            contact != null ? nvl(contact.getEmail()) : "",
            contact != null ? nvl(contact.getPhone()) : "",
            address != null ? nvl(address.getCity()) : "",
            address != null ? nvl(address.getCountry()) : "",
            pi != null ? nvl(pi.getRole()) : "",
            currentEmployer,
            yearsExp,
            resolveHighestEducation(cv),
            skills,
            languages,
            socialLinks != null ? nvl(socialLinks.getLinkedin()) : "",
            "Qorva AI Import",
            cv != null ? nvl(cv.getApplicantNumber()) : "",
            scoreStr,
            rank,
            seniority,
            primaryCluster,
            secondaryCluster,
            envFit,
            resolveMandatorySkillsMet(missingSkills),
            matchStrengths,
            matchGaps,
            buildNotesBlock(score, rank, matchStrengths, matchGaps, seniority, primaryCluster, envFitFirst)
        ));

        if (euFormat) {
            row.add(buildTagsColumn(cv, clustering));
            row.add(resolveNoticePeriod(pi));
            row.add(resolveCvAgeMonths(cv));
            row.add("N/A - verify with candidate");
            row.add("Qorva AI");
        }

        return row;
    }

    private String resolveHighestEducation(CVDTO cv) {
        if (cv == null || CollectionUtils.isEmpty(cv.getEducation())) return "";
        return cv.getEducation().stream()
            .map(Education::getDegree)
            .filter(d -> d != null && !d.isBlank())
            .max(Comparator.comparingInt(d -> {
                String lower = d.toLowerCase();
                return DEGREE_RANK.entrySet().stream()
                    .filter(e -> lower.contains(e.getKey()))
                    .mapToInt(Map.Entry::getValue)
                    .max()
                    .orElse(0);
            }))
            .orElse("");
    }

    private String resolveMandatorySkillsMet(MissingSkills missingSkills) {
        if (missingSkills == null || CollectionUtils.isEmpty(missingSkills.getSkills())) return "Yes";
        boolean hasHighImportance = missingSkills.getSkills().stream()
            .anyMatch(s -> "high".equalsIgnoreCase(s.getImportance()));
        return hasHighImportance ? "No" : "Partial";
    }

    private String buildNotesBlock(double score, int rank, String strengths, String gaps,
                                   String seniority, String primaryCluster, String envFit) {
        return String.format(
            "[Qorva AI - Match Score: %.0f%% | Rank: #%d]%nStrengths: %s%nGaps: %s%nSeniority: %s | Cluster: %s | Env Fit: %s",
            score, rank,
            strengths.isBlank() ? "N/A" : strengths,
            gaps.isBlank() ? "N/A" : gaps,
            seniority, primaryCluster, envFit
        );
    }

    private String buildTagsColumn(CVDTO cv, CandidateClustering clustering) {
        List<String> tags = new ArrayList<>();
        if (cv != null && !CollectionUtils.isEmpty(cv.getKeySkills())) {
            cv.getKeySkills().stream()
                .filter(ks -> ks.getSkills() != null)
                .flatMap(ks -> ks.getSkills().stream())
                .forEach(tags::add);
        }
        if (clustering != null) {
            if (clustering.getSeniorityLevel() != null) tags.add(humanizeSeniority(clustering.getSeniorityLevel()));
            if (clustering.getPrimaryCluster() != null) tags.add(clustering.getPrimaryCluster());
        }
        return String.join(", ", tags);
    }

    private String resolveNoticePeriod(PersonalInformation pi) {
        if (pi == null || pi.getAvailability() == null || pi.getAvailability().getNoticePeriodDays() == null) return "";
        return String.valueOf(pi.getAvailability().getNoticePeriodDays());
    }

    private long resolveCvAgeMonths(CVDTO cv) {
        if (cv == null || cv.getCreatedAt() == null) return 0;
        return ChronoUnit.MONTHS.between(
            cv.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(),
            LocalDate.now(ZoneOffset.UTC)
        );
    }

    private String formatLanguage(Language lang) {
        if (lang == null) return "";
        String name = nvl(lang.getLanguage());
        Proficiency p = lang.getProficiency();
        if (p == null) return name;
        String level = p.getSpoken() != null ? p.getSpoken()
            : (p.getWritten() != null ? p.getWritten() : p.getRead());
        return level != null ? name + " (" + level + ")" : name;
    }

    private String humanizeSeniority(String value) {
        if (value == null) return "";
        return switch (value) {
            case "junior"    -> "Junior";
            case "midLevel"  -> "Mid-Level";
            case "senior"    -> "Senior";
            case "lead"      -> "Lead";
            case "principal" -> "Principal";
            case "manager"   -> "Manager";
            case "director"  -> "Director";
            case "executive" -> "Executive";
            default          -> value;
        };
    }

    private static String[] concat(String[] a, String[] b) {
        String[] result = new String[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static String nvl(String value) {
        return value != null ? value : "";
    }
}
