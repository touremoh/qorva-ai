package ai.qorva.core.controller;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.dto.ResumeMatchDTO;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.mapper.requests.ResumeMatchRequestMapper;
import ai.qorva.core.service.QorvaUserDetailsService;
import ai.qorva.core.service.ResumeMatchService;
import ai.qorva.core.utils.BuildApiResponse;
import ai.qorva.core.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/resume-matches")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class ResumeMatchController extends AbstractQorvaController<ResumeMatchDTO> {

	@Autowired
	public ResumeMatchController(ResumeMatchService service, ResumeMatchRequestMapper requestMapper, QorvaUserDetailsService userService, JwtConfig jwtConfig) {
		super(service, requestMapper, userService, jwtConfig);
	}

	@GetMapping("/check/monthly-usage/{tenantId}")
	public ResponseEntity<QorvaRequestResponse> checkCVAnalysisMonthlyUsageLimit(@PathVariable String tenantId) throws QorvaException {
		return BuildApiResponse.from(((ResumeMatchService)service).checkCVAnalysisMonthlyUsageLimit(tenantId));
	}

	@GetMapping("/search")
	public ResponseEntity<QorvaRequestResponse> searchAll(
		@RequestHeader("Authorization") String authorizationHeader,
		@RequestParam("searchTerms") String searchTerms,
		@RequestParam("pageSize") int pageSize,
		@RequestParam("pageNumber") int pageNumber) throws QorvaException {
		var tenantId = JwtUtils.extractTenantId(JwtUtils.extractToken(authorizationHeader), this.jwtConfig.getSecretKey());
		return BuildApiResponse.from(((ResumeMatchService)this.service).searchAll(tenantId, searchTerms, pageSize, pageNumber));
	}
}
