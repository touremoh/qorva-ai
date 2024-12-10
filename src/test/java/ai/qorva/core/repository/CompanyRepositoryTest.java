package ai.qorva.core.repository;

import ai.qorva.core.dao.entity.Company;
import ai.qorva.core.dao.repository.CompanyRepository;
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

class CompanyRepositoryTest extends AbstractRepositoryTest<Company> {

    private CompanyRepository repository;

    @BeforeEach
    public void init() {
        super.setUp();
        repository = new CompanyRepository(mongoTemplate);
    }

    @Test
    void testFindOneById() {
        String id = "testId";
        Company company = new Company();
        company.setId(id);

        when(mongoTemplate.findById(id, Company.class)).thenReturn(company);

        Optional<Company> result = repository.findOneById(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        verify(mongoTemplate, times(1)).findById(id, Company.class);
    }

    @Test
    void testFindOneByData() {
        Company company = new Company();
        company.setName("Test Company");

        when(mongoTemplate.findOne(any(Query.class), eq(Company.class))).thenReturn(company);

        Optional<Company> result = repository.findOneByData(company);

        assertTrue(result.isPresent());
        assertEquals("Test Company", result.get().getName());
        verify(mongoTemplate, times(1)).findOne(any(Query.class), eq(Company.class));
    }

    @Test
    void testCreateOne() {
        Company company = new Company();
        company.setName("Test Company");

        when(mongoTemplate.insert(company)).thenReturn(company);

        Company result = repository.createOne(company);

        assertNotNull(result);
        assertEquals("Test Company", result.getName());
        verify(mongoTemplate, times(1)).insert(company);
    }

    @Test
    void testFindMany() {
        Company company1 = new Company();
        company1.setName("Company 1");
        Company company2 = new Company();
        company2.setName("Company 2");

        when(mongoTemplate.find(any(), eq(Company.class))).thenReturn(List.of(company1, company2));
        when(mongoTemplate.count(any(), eq(Company.class))).thenReturn(2L);

        Page<Company> result = repository.findMany(0, 2);

        assertEquals(2, result.getTotalElements());
        assertEquals("Company 1", result.getContent().get(0).getName());
        assertEquals("Company 2", result.getContent().get(1).getName());
        verify(mongoTemplate, times(1)).find(any(), eq(Company.class));
        verify(mongoTemplate, times(1)).count(any(), eq(Company.class));
    }

    @Test
    void testFindManyByIds() {
        List<String> ids = List.of("id1", "id2");
        Company company1 = new Company();
        company1.setId("id1");
        Company company2 = new Company();
        company2.setId("id2");

        when(mongoTemplate.find(any(), eq(Company.class))).thenReturn(List.of(company1, company2));

        Page<Company> result = repository.findManyByIds(ids);

        assertEquals(2, result.getTotalElements());
        assertEquals("id1", result.getContent().get(0).getId());
        assertEquals("id2", result.getContent().get(1).getId());
        verify(mongoTemplate, times(1)).find(any(), eq(Company.class));
    }

    @Test
    void testUpdateOne() {
        String id = "testId";

        Company originalCompany = new Company();
        originalCompany.setId(id);
        originalCompany.setName("Original Name");

        Company updatedCompany = new Company();
        updatedCompany.setName("Updated Name");

        when(mongoTemplate.findOne(any(Query.class), eq(Company.class))).thenReturn(originalCompany);
        when(mongoTemplate.updateFirst(any(Query.class), any(), eq(Company.class)))
            .thenReturn(Mockito.mock(UpdateResult.class));
        when(mongoTemplate.findById(id, Company.class)).thenReturn(updatedCompany);

        Optional<Company> result = repository.updateOne(id, updatedCompany);

        assertTrue(result.isPresent());
        assertEquals("Updated Name", result.get().getName());
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(), eq(Company.class));
        verify(mongoTemplate, times(1)).findById(id, Company.class);
    }

    @Test
    void testDeleteOneById() {
        String id = "testId";

        when(mongoTemplate.remove(any(Query.class), eq(Company.class)))
            .thenReturn(Mockito.mock(DeleteResult.class));

        boolean result = repository.deleteOneById(id);

        assertFalse(result);
        verify(mongoTemplate, times(1)).remove(any(Query.class), eq(Company.class));
    }

    @Test
    void testExistsByData() {
        Company company = new Company();
        company.setName("Test Company");

        when(mongoTemplate.exists(any(Query.class), eq(Company.class))).thenReturn(true);

        boolean exists = repository.existsByData(company);

        assertTrue(exists);
        verify(mongoTemplate, times(1)).exists(any(Query.class), eq(Company.class));
    }
}
