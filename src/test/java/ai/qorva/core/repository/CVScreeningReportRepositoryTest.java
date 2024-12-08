package ai.qorva.core.repository;

import ai.qorva.core.dao.entity.CVScreeningReport;
import ai.qorva.core.dao.repository.CVScreeningReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CVScreeningReportRepositoryTest extends AbstractRepositoryTest<CVScreeningReport> {

    private CVScreeningReportRepository repository;

    @BeforeEach
    public void init() {
        super.setUp();
        repository = new CVScreeningReportRepository(mongoTemplate);
    }

    @Test
    void testFindOneById() {
        String id = "testId";
        CVScreeningReport report = new CVScreeningReport();
        report.setId(id);

        when(mongoTemplate.findById(id, CVScreeningReport.class)).thenReturn(report);

        Optional<CVScreeningReport> result = repository.findOneById(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        verify(mongoTemplate, times(1)).findById(id, CVScreeningReport.class);
    }

    @Test
    void testFindOneByData() {
        CVScreeningReport report = new CVScreeningReport();
        report.setJobTitle("Data Analyst");

        when(mongoTemplate.findOne(any(), eq(CVScreeningReport.class))).thenReturn(report);

        Optional<CVScreeningReport> result = repository.findOneByData(report);

        assertTrue(result.isPresent());
        assertEquals("Data Analyst", result.get().getJobTitle());
        verify(mongoTemplate, times(1)).findOne(any(), eq(CVScreeningReport.class));
    }

    @Test
    void testCreateOne() {
        CVScreeningReport report = new CVScreeningReport();
        report.setJobTitle("Data Scientist");

        when(mongoTemplate.insert(report)).thenReturn(report);

        CVScreeningReport result = repository.createOne(report);

        assertNotNull(result);
        assertEquals("Data Scientist", result.getJobTitle());
        verify(mongoTemplate, times(1)).insert(report);
    }

    @Test
    void testFindMany() {
        CVScreeningReport report1 = new CVScreeningReport();
        report1.setJobTitle("Analyst");
        CVScreeningReport report2 = new CVScreeningReport();
        report2.setJobTitle("Engineer");

        when(mongoTemplate.find(any(), eq(CVScreeningReport.class))).thenReturn(List.of(report1, report2));

        List<CVScreeningReport> result = repository.findMany(0, 2);

        assertEquals(2, result.size());
        assertEquals("Analyst", result.get(0).getJobTitle());
        assertEquals("Engineer", result.get(1).getJobTitle());
        verify(mongoTemplate, times(1)).find(any(), eq(CVScreeningReport.class));
    }

    @Test
    void testFindManyByIds() {
        List<String> ids = List.of("id1", "id2");
        CVScreeningReport report1 = new CVScreeningReport();
        report1.setId("id1");
        CVScreeningReport report2 = new CVScreeningReport();
        report2.setId("id2");

        when(mongoTemplate.find(any(), eq(CVScreeningReport.class))).thenReturn(List.of(report1, report2));

        List<CVScreeningReport> result = repository.findManyByIds(ids);

        assertEquals(2, result.size());
        assertEquals("id1", result.get(0).getId());
        assertEquals("id2", result.get(1).getId());
        verify(mongoTemplate, times(1)).find(any(), eq(CVScreeningReport.class));
    }

    @Test
    void testUpdateOne() {
        String id = "testId";

        CVScreeningReport originalReport = new CVScreeningReport();
        originalReport.setId(id);
        originalReport.setJobTitle("Original Title");

        CVScreeningReport updatedReport = new CVScreeningReport();
        updatedReport.setJobTitle("Updated Title");

        when(mongoTemplate.findOne(any(), eq(CVScreeningReport.class))).thenReturn(originalReport);
        when(mongoTemplate.updateFirst(any(), any(), eq(CVScreeningReport.class)))
            .thenReturn(mock(UpdateResult.class));
        when(mongoTemplate.findById(id, CVScreeningReport.class)).thenReturn(updatedReport);

        Optional<CVScreeningReport> result = repository.updateOne(id, updatedReport);

        assertTrue(result.isPresent());
        assertEquals("Updated Title", result.get().getJobTitle());
        verify(mongoTemplate, times(1)).updateFirst(any(), any(), eq(CVScreeningReport.class));
        verify(mongoTemplate, times(1)).findById(id, CVScreeningReport.class);
    }

    @Test
    void testDeleteOneById() {
        String id = "testId";

        when(mongoTemplate.remove(any(), eq(CVScreeningReport.class))).thenReturn(mock(DeleteResult.class));

        boolean result = repository.deleteOneById(id);

        assertFalse(result); // Assuming delete result mock returns 0 deletions
        verify(mongoTemplate, times(1)).remove(any(), eq(CVScreeningReport.class));
    }

    @Test
    void testExistsByData() {
        CVScreeningReport report = new CVScreeningReport();
        report.setJobTitle("Test Job");

        when(mongoTemplate.exists(any(), eq(CVScreeningReport.class))).thenReturn(true);

        boolean exists = repository.existsByData(report);

        assertTrue(exists);
        verify(mongoTemplate, times(1)).exists(any(), eq(CVScreeningReport.class));
    }
}
