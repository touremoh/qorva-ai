package ai.qorva.core.repository;

import ai.qorva.core.dao.entity.CV;
import ai.qorva.core.dao.repository.CVRepository;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CVRepositoryTest extends AbstractRepositoryTest<CV> {

    private CVRepository repository;

    @BeforeEach
    public void init() {
        super.setUp();
        repository = new CVRepository(mongoTemplate);
    }

    @Test
    void testFindOneById() {
        String id = "testId";
        CV cv = new CV();
        cv.setId(id);

        when(mongoTemplate.findById(id, CV.class)).thenReturn(cv);

        Optional<CV> result = repository.findOneById(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        verify(mongoTemplate, times(1)).findById(id, CV.class);
    }

    @Test
    void testFindOneByData() {
        CV cv = new CV();
        cv.setCompanyId("companyId");

        when(mongoTemplate.findOne(any(Query.class), eq(CV.class))).thenReturn(cv);

        Optional<CV> result = repository.findOneByData(cv);

        assertTrue(result.isPresent());
        assertEquals("companyId", result.get().getCompanyId());
        verify(mongoTemplate, times(1)).findOne(any(Query.class), eq(CV.class));
    }

    @Test
    void testCreateOne() {
        CV cv = new CV();
        cv.setCompanyId("companyId");

        when(mongoTemplate.insert(cv)).thenReturn(cv);

        CV result = repository.createOne(cv);

        assertNotNull(result);
        assertEquals("companyId", result.getCompanyId());
        verify(mongoTemplate, times(1)).insert(cv);
    }

    @Test
    void testFindMany() {
        CV cv1 = new CV();
        cv1.setCompanyId("CompanyId1");
        CV cv2 = new CV();
        cv2.setCompanyId("CompanyId2");

        when(mongoTemplate.find(any(), eq(CV.class))).thenReturn(List.of(cv1, cv2));
        when(mongoTemplate.count(any(), eq(CV.class))).thenReturn(2L);

        Page<CV> result = repository.findMany(0, 2);

        assertEquals(2, result.getTotalElements());
        assertEquals("CompanyId1", result.getContent().get(0).getCompanyId());
        assertEquals("CompanyId2", result.getContent().get(1).getCompanyId());
        verify(mongoTemplate, times(1)).find(any(), eq(CV.class));
        verify(mongoTemplate, times(1)).count(any(), eq(CV.class));
    }

    @Test
    void testFindManyByIds() {
        List<String> ids = List.of("id1", "id2");
        CV cv1 = new CV();
        cv1.setId("id1");
        CV cv2 = new CV();
        cv2.setId("id2");

        when(mongoTemplate.find(any(), eq(CV.class))).thenReturn(List.of(cv1, cv2));

        Page<CV> result = repository.findManyByIds(ids);

        assertEquals(2, result.getTotalElements());
        assertEquals("id1", result.getContent().get(0).getId());
        assertEquals("id2", result.getContent().get(1).getId());
        verify(mongoTemplate, times(1)).find(any(), eq(CV.class));
    }

    @Test
    void testUpdateOne() {
        String id = "testId";

        CV originalCV = new CV();
        originalCV.setId(id);
        originalCV.setCompanyId("originalCompanyId");

        CV updatedCV = new CV();
        updatedCV.setCompanyId("updatedCompanyId");

        when(mongoTemplate.findOne(any(Query.class), eq(CV.class))).thenReturn(originalCV);
        when(mongoTemplate.updateFirst(any(Query.class), any(), eq(CV.class)))
            .thenReturn(Mockito.mock(UpdateResult.class));
        when(mongoTemplate.findById(id, CV.class)).thenReturn(updatedCV);

        Optional<CV> result = repository.updateOne(id, updatedCV);

        assertTrue(result.isPresent());
        assertEquals("updatedCompanyId", result.get().getCompanyId());
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(), eq(CV.class));
        verify(mongoTemplate, times(1)).findById(id, CV.class);
    }

    @Test
    void testDeleteOneById() {
        String id = "testId";

        when(mongoTemplate.remove(any(Query.class), eq(CV.class)))
            .thenReturn(Mockito.mock(DeleteResult.class));

        boolean result = repository.deleteOneById(id);

        assertFalse(result);
        verify(mongoTemplate, times(1)).remove(any(Query.class), eq(CV.class));
    }

    @Test
    void testExistsByData() {
        CV cv = new CV();
        cv.setCompanyId("companyId");

        when(mongoTemplate.exists(any(Query.class), eq(CV.class))).thenReturn(true);

        boolean exists = repository.existsByData(cv);

        assertTrue(exists);
        verify(mongoTemplate, times(1)).exists(any(Query.class), eq(CV.class));
    }
}
