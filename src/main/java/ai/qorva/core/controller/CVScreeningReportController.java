package ai.qorva.core.controller;

import ai.qorva.core.dto.CVScreeningReportDTO;
import ai.qorva.core.service.CVScreeningReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
public class CVScreeningReportController extends AbstractQorvaController<CVScreeningReportDTO> {

    @Autowired
    public CVScreeningReportController(CVScreeningReportService service) {
        super(service);
    }
}
