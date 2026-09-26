package ai.qorva.core.controller;

import ai.qorva.core.dto.JobDescriptionData;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.dto.common.ScoringRules;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.security.CrudPolicy;
import ai.qorva.core.service.JobDescriptionBuilderService;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.ScoringRulesPrefillService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static ai.qorva.core.security.CrudOperation.*;

@RestController
@RequestMapping("/jobs")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class JobPostController extends AbstractQorvaController<JobPostDTO> {

    private final ScoringRulesPrefillService scoringRulesPrefillService;
    private final JobDescriptionBuilderService jobDescriptionBuilderService;

    @Autowired
    public JobPostController(JobPostService service, ScoringRulesPrefillService scoringRulesPrefillService,
                             JobDescriptionBuilderService jobDescriptionBuilderService) {
        super(service);
        this.scoringRulesPrefillService = scoringRulesPrefillService;
        this.jobDescriptionBuilderService = jobDescriptionBuilderService;
    }

    /**
     * AI job-description builder: structured inputs -> ready-to-edit JD + suggested
     * scoring rules. Free feature (unmetered) — screening actions bill at screening time.
     */
    @PostMapping("/description/generate")
    @PreAuthorize("@accessManager.hasPermission(authentication,'ADD_JOB')")
    public ResponseEntity<JobDescriptionData.GenerateResponse> generateDescription(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "en") String language,
            @RequestBody @Valid JobDescriptionData.GenerateRequest request) throws QorvaException {
        return ResponseEntity.ok(this.jobDescriptionBuilderService.generate(currentTenantId(), request, language));
    }

    public record SuggestScoringRulesRequest(String title, String description) {}

    /**
     * AI-drafts the scoring rules from a job title/description (job-post creation wizard).
     * Create flow only — the frontend never calls this for updates, where a human already
     * invested in the rules.
     */
    @PostMapping("/scoring-rules/suggest")
    @PreAuthorize("@accessManager.hasPermission(authentication,'ADD_JOB')")
    public ResponseEntity<ScoringRules> suggestScoringRules(
            @RequestBody SuggestScoringRulesRequest request) throws QorvaException {
        return ResponseEntity.ok(this.scoringRulesPrefillService.suggest(
            currentTenantId(), request.title(), request.description()));
    }

    /* Reads need VIEW_JOB and writes their own action, so demo users (who lack ADD_JOB/MODIFY_JOB/DELETE_JOB) cannot mutate data. */
    @Override
    protected CrudPolicy crudPolicy() {
        return CrudPolicy.builder()
            .allow("VIEW_JOB", GET_ONE, LIST, SEARCH, FIND_BY_IDS, EXISTS)
            .allow("ADD_JOB", CREATE)
            .allow("MODIFY_JOB", UPDATE)
            .allow("DELETE_JOB", DELETE)
            .build();
    }
}
