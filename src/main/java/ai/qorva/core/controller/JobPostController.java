package ai.qorva.core.controller;

import ai.qorva.core.config.JwtConfig;
import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.mapper.requests.JobPostRequestMapper;
import ai.qorva.core.service.JobPostService;
import ai.qorva.core.service.QorvaUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class JobPostController extends AbstractQorvaController<JobPostDTO> {

    @Autowired
    public JobPostController(JobPostService service, JobPostRequestMapper requestMapper, QorvaUserDetailsService userService, JwtConfig jwtConfig) {
        super(service, requestMapper, userService, jwtConfig);
    }
}
