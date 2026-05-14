package ai.qorva.core.controller;

import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.MatchingReportDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.MatchingReportService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/matching-reports")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class MatchingReportController extends AbstractQorvaController<MatchingReportDTO> {

	@Autowired
	public MatchingReportController(MatchingReportService service) {
		super(service);
	}

	@GetMapping("/check/monthly-usage")
	public ResponseEntity<QorvaRequestResponse> checkCVAnalysisMonthlyUsageLimit() throws QorvaException {
		return BuildApiResponse.from(((MatchingReportService) service).checkCVAnalysisMonthlyUsageLimit(currentTenantId()));
	}

	@GetMapping("/search")
	public ResponseEntity<QorvaRequestResponse> searchAll(@RequestParam Map<String, String> params) throws QorvaException {
		params.put("tenantId", currentTenantId());
		return BuildApiResponse.from(((MatchingReportService) this.service).searchAll(params));
	}
}
