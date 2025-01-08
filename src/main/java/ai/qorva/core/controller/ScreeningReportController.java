package ai.qorva.core.controller;

import ai.qorva.core.dto.ScreeningReportDTO;
import ai.qorva.core.dto.GenerateReportRequest;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.ScreeningReportService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
@CrossOrigin(origins = "${weblink.allowedOrigin}")
public class ScreeningReportController extends AbstractQorvaController<ScreeningReportDTO> {

    @Autowired
    public ScreeningReportController(ScreeningReportService service) {
        super(service);
	}

    @PostMapping(value = "/generate")
    public ResponseEntity<QorvaRequestResponse> generateReport(
        @RequestHeader("Accept-Language") String languageCode,
        @RequestBody GenerateReportRequest request
    ) throws QorvaException {
        return BuildApiResponse.from(((ScreeningReportService) service).generateReport(request.getJobPostId(), request.getCvIDs(), languageCode));
    }
}
