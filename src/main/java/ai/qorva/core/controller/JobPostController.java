package ai.qorva.core.controller;

import ai.qorva.core.dto.JobPostDTO;
import ai.qorva.core.service.JobPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
@CrossOrigin(origins = "${weblink.allowedOrigins}")
public class JobPostController extends AbstractQorvaController<JobPostDTO> {

    @Autowired
    public JobPostController(JobPostService service) {
        super(service);
    }
}
