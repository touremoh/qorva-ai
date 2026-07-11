package ai.qorva.core.service.orchestrators.handlers;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.CVRepository;
import ai.qorva.core.dao.repository.JobPostRepository;
import ai.qorva.core.dao.specifications.CVSpecifications;
import ai.qorva.core.dao.specifications.JobPostSpecifications;
import ai.qorva.core.dao.specifications.MongoSpecification;
import ai.qorva.core.dto.*;
import ai.qorva.core.dto.common.Certification;
import ai.qorva.core.dto.common.SkillRequirement;
import ai.qorva.core.service.orchestrators.MentionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateComparisonHandler implements InsightHandler {

    private final CVRepository cvRepository;
    private final JobPostRepository jobPostRepository;

    @Override
    public InsightHandlerResult handle(CVQueryParams params, ObjectId tenantId) {
        return handle(params, tenantId, null);
    }

    @Override
    public InsightHandlerResult handle(CVQueryParams params, ObjectId tenantId, MentionResolver.ResolvedMentions mentions) {
        String tenantIdStr = tenantId.toHexString();

        // Resolve candidates: prefer @-mentioned candidates over LLM-extracted applicantNumbers
        List<CV> cvs;
        if (mentions != null && mentions.candidates().size() >= 2) {
            cvs = mentions.candidates();
        } else {
            List<String> applicantNumbers = params.applicantNumbers();
            if (applicantNumbers == null || applicantNumbers.size() < 2) {
                return clarificationResult("Please provide at least 2 candidates to compare — either @-mention them by name or use their reference numbers (e.g. 'Compare REF-001 and REF-002').");
            }
            cvs = cvRepository.findAll(
                MongoSpecification.where(CVSpecifications.tenantIdEquals(tenantIdStr))
                    .and(CVSpecifications.applicantNumberIn(applicantNumbers))
            );
            if (cvs.size() < 2) {
                return clarificationResult(
                    "Could not find enough candidates with the provided reference numbers. Please verify the references and try again."
                );
            }
        }

        // Resolve job post: prefer @-mentioned job over LLM-extracted jobPostReference
        Map<String, Object> jobPostSnapshot = null;
        if (mentions != null && !mentions.jobs().isEmpty()) {
            jobPostSnapshot = buildJobPostSnapshot(mentions.jobs().get(0));
        } else if (params.jobPostReference() != null && !params.jobPostReference().isBlank()) {
            Optional<JobPost> jobPost = jobPostRepository.findOne(
                MongoSpecification.where(JobPostSpecifications.tenantIdEquals(tenantIdStr))
                    .and(JobPostSpecifications.jobReferenceEquals(params.jobPostReference()))
            );
            if (jobPost.isEmpty()) {
                return clarificationResult(
                    "Job reference '" + params.jobPostReference() + "' was not found. Please verify the job reference and try again."
                );
            }
            jobPostSnapshot = buildJobPostSnapshot(jobPost.get());
        }

        List<CandidateCardDTO> cards = cvs.stream()
            .map(CandidateCardMapper::toCard)
            .collect(Collectors.toList());

        List<Set<String>> skillSets = cvs.stream()
            .map(this::extractSkillSet)
            .collect(Collectors.toList());

        Set<String> commonSkills = skillSets.stream()
            .reduce(new HashSet<>(skillSets.get(0)), (intersection, next) -> {
                intersection.retainAll(next);
                return intersection;
            });

        // Extended per-candidate details keyed by applicantNumber for easy frontend lookup
        Map<String, Map<String, Object>> candidateDetails = new LinkedHashMap<>();
        Map<String, List<String>> differentiatingSkills = new LinkedHashMap<>();
        for (int i = 0; i < cvs.size(); i++) {
            CV cv = cvs.get(i);
            String ref = cv.getApplicantNumber() != null ? cv.getApplicantNumber() : cv.getId();
            candidateDetails.put(ref, buildExtendedDetails(cv));
            Set<String> unique = new HashSet<>(skillSets.get(i));
            unique.removeAll(commonSkills);
            differentiatingSkills.put(ref, new ArrayList<>(unique));
        }

        Map<String, Object> rawData = new LinkedHashMap<>();
        rawData.put("comparisonType", jobPostSnapshot != null ? "AGAINST_JOB" : "CANDIDATES_ONLY");
        rawData.put("commonSkills", new ArrayList<>(commonSkills));
        rawData.put("differentiatingSkills", differentiatingSkills);
        rawData.put("candidateDetails", candidateDetails);
        if (jobPostSnapshot != null) {
            rawData.put("jobPost", jobPostSnapshot);
        }

        return new InsightHandlerResult(cards, cvs.size(), List.of(), List.of(), rawData);
    }

    // Extended fields not present in CandidateCardDTO, keyed by applicantNumber in rawData
    private Map<String, Object> buildExtendedDetails(CV cv) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("yearsOfExperience", cv.getNbYearsOfExperience());
        details.put("leadership", cv.getCandidateClustering() != null ? cv.getCandidateClustering().getLeadershipAndInfluence() : null);
        details.put("skillDepth", cv.getCandidateClustering() != null ? cv.getCandidateClustering().getSkillDepth() : null);
        details.put("industries", cv.getCandidateClustering() != null ? cv.getCandidateClustering().getIndustryDomains() : List.of());
        details.put("skills", extractSkillList(cv));
        details.put("highestDegree", extractHighestDegree(cv));
        details.put("certifications", extractCertifications(cv));
        return details;
    }

    private Map<String, Object> buildJobPostSnapshot(JobPost jobPost) {
        List<String> requiredSkills = List.of();
        if (jobPost.getScoringRules() != null && jobPost.getScoringRules().getSkills() != null) {
            requiredSkills = jobPost.getScoringRules().getSkills().stream()
                .map(SkillRequirement::name)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toList());
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("jobReference", jobPost.getJobReference());
        snapshot.put("title", jobPost.getTitle());
        snapshot.put("requiredSkills", requiredSkills);
        return snapshot;
    }

    private Set<String> extractSkillSet(CV cv) {
        return new HashSet<>(extractSkillList(cv));
    }

    private List<String> extractSkillList(CV cv) {
        if (cv.getKeySkills() == null) return List.of();
        return cv.getKeySkills().stream()
            .filter(ks -> ks.getSkills() != null)
            .flatMap(ks -> ks.getSkills().stream())
            .filter(s -> s != null && !s.isBlank())
            .collect(Collectors.toList());
    }

    private String extractHighestDegree(CV cv) {
        if (cv.getEducation() == null || cv.getEducation().isEmpty()) return null;
        return cv.getEducation().get(0).getDegree();
    }

    private List<String> extractCertifications(CV cv) {
        if (cv.getCertifications() == null) return List.of();
        return cv.getCertifications().stream()
            .map(Certification::getTitle)
            .filter(t -> t != null && !t.isBlank())
            .collect(Collectors.toList());
    }

    private InsightHandlerResult clarificationResult(String message) {
        Map<String, Object> rawData = new LinkedHashMap<>();
        rawData.put("needsClarification", true);
        rawData.put("clarificationMessage", message);
        return new InsightHandlerResult(List.of(), 0, List.of(), List.of(), rawData);
    }
}
