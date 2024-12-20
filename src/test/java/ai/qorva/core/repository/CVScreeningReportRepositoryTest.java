package ai.qorva.core.repository;

import ai.qorva.core.dao.entity.CVScreeningReport;
import ai.qorva.core.dao.repository.CVScreeningReportRepository;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

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

        Optional<CVScreeningReport> result = repository.findOneById(id.toString());

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        verify(mongoTemplate, times(1)).findById(id, CVScreeningReport.class);
    }

    @Test
    void testFindOneByData() {
        CVScreeningReport report = new CVScreeningReport();
        report.setCandidateName("John Doe");

        when(mongoTemplate.findOne(any(), eq(CVScreeningReport.class))).thenReturn(report);

        Optional<CVScreeningReport> result = repository .findOneByData(report);

        assertTrue(result.isPresent());
        assertEquals("John Doe", result.get().getCandidateName());
        verify(mongoTemplate, times(1)).findOne(any(), eq(CVScreeningReport.class));
    }

    @Test
    void testCreateOne() {
        CVScreeningReport report = new CVScreeningReport();
        report.setCandidateName("John Doe");

        when(mongoTemplate.insert(report)).thenReturn(report);

        CVScreeningReport result = repository.createOne(report);

        assertNotNull(result);
        assertEquals("John Doe", result.getCandidateName());
        verify(mongoTemplate, times(1)).insert(report);
    }

    @Test
    void testFindMany() {
        CVScreeningReport report1 = new CVScreeningReport();
        report1.setCandidateName("John Doe");
        CVScreeningReport report2 = new CVScreeningReport();
        report2.setCandidateName("Jane Smith");

        when(mongoTemplate.find(any(), eq(CVScreeningReport.class))).thenReturn(List.of(report1, report2));
        when(mongoTemplate.count(any(), eq(CVScreeningReport.class))).thenReturn(2L);

        Page<CVScreeningReport> result = repository.findMany(0, 2);

        assertEquals(2, result.getTotalElements());
        assertEquals("John Doe", result.getContent().get(0).getCandidateName());
        assertEquals("Jane Smith", result.getContent().get(1).getCandidateName());
        verify(mongoTemplate, times(1)).find(any(), eq(CVScreeningReport.class));
        verify(mongoTemplate, times(1)).count(any(), eq(CVScreeningReport.class));
    }

    @Test
    void testFindManyByIds() {
        List<String> ids = List.of("id1", "id2");
        CVScreeningReport report1 = new CVScreeningReport();
        report1.setId("id1");
        CVScreeningReport report2 = new CVScreeningReport();
        report2.setId("id2");

        when(mongoTemplate.find(any(), eq(CVScreeningReport.class))).thenReturn(List.of(report1, report2));

        Page<CVScreeningReport> result = repository.findManyByIds(ids);

        assertEquals(2, result.getTotalElements());
        assertEquals("id1", result.getContent().get(0).getId());
        assertEquals("id2", result.getContent().get(1).getId());
        verify(mongoTemplate, times(1)).find(any(), eq(CVScreeningReport.class));
    }

    @Test
    void testUpdateOne() {
        String id = "testId";
        CVScreeningReport updatedReport = new CVScreeningReport();
        updatedReport.setCandidateName("Updated Name");

        when(mongoTemplate.findOne(any(), eq(CVScreeningReport.class))).thenReturn(updatedReport);
        when(mongoTemplate.updateFirst(any(), any(), eq(CVScreeningReport.class))).thenReturn(mock(UpdateResult.class));
        when(mongoTemplate.findById(id, CVScreeningReport.class)).thenReturn(updatedReport);

        Optional<CVScreeningReport> result = repository.updateOne(id, updatedReport);

        assertTrue(result.isPresent());
        assertEquals("Updated Name", result.get().getCandidateName());
        verify(mongoTemplate, times(1)).updateFirst(any(), any(), eq(CVScreeningReport.class));
        verify(mongoTemplate, times(1)).findById(id, CVScreeningReport.class);
    }

    @Test
    void testDeleteOneById() {
        String id = "testId";

        DeleteResult deleteResult = mock(DeleteResult.class);
        when(deleteResult.getDeletedCount()).thenReturn(1L);
        when(mongoTemplate.remove(any(), eq(CVScreeningReport.class))).thenReturn(deleteResult);

        boolean result = repository.deleteOneById(id);

        assertTrue(result);
        verify(mongoTemplate, times(1)).remove(any(), eq(CVScreeningReport.class));
    }
}
