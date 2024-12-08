package ai.qorva.core.repository;

import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.JobPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobPostRepositoryTest extends AbstractRepositoryTest<JobPost> {

    private JobPostRepository repository;

    @BeforeEach
    public void init() {
        super.setUp();
        repository = new JobPostRepository(mongoTemplate);
    }

    @Test
    void testFindOneById() {
        String id = "testId";
        JobPost jobPost = new JobPost();
        jobPost.setId(id);

        when(mongoTemplate.findById(id, JobPost.class)).thenReturn(jobPost);

        Optional<JobPost> result = repository.findOneById(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        verify(mongoTemplate, times(1)).findById(id, JobPost.class);
    }

    @Test
    void testFindOneByData() {
        JobPost jobPost = new JobPost();
        jobPost.setTitle("Software Engineer");

        when(mongoTemplate.findOne(any(), eq(JobPost.class))).thenReturn(jobPost);

        Optional<JobPost> result = repository.findOneByData(jobPost);

        assertTrue(result.isPresent());
        assertEquals("Software Engineer", result.get().getTitle());
        verify(mongoTemplate, times(1)).findOne(any(), eq(JobPost.class));
    }

    @Test
    void testCreateOne() {
        JobPost jobPost = new JobPost();
        jobPost.setTitle("Backend Developer");

        when(mongoTemplate.insert(jobPost)).thenReturn(jobPost);

        JobPost result = repository.createOne(jobPost);

        assertNotNull(result);
        assertEquals("Backend Developer", result.getTitle());
        verify(mongoTemplate, times(1)).insert(jobPost);
    }

    @Test
    void testFindMany() {
        JobPost jobPost1 = new JobPost();
        jobPost1.setTitle("Developer");
        JobPost jobPost2 = new JobPost();
        jobPost2.setTitle("Tester");

        when(mongoTemplate.find(any(), eq(JobPost.class))).thenReturn(List.of(jobPost1, jobPost2));

        List<JobPost> result = repository.findMany(0, 2);

        assertEquals(2, result.size());
        assertEquals("Developer", result.get(0).getTitle());
        assertEquals("Tester", result.get(1).getTitle());
        verify(mongoTemplate, times(1)).find(any(), eq(JobPost.class));
    }

    @Test
    void testFindManyByIds() {
        List<String> ids = List.of("id1", "id2");
        JobPost jobPost1 = new JobPost();
        jobPost1.setId("id1");
        JobPost jobPost2 = new JobPost();
        jobPost2.setId("id2");

        when(mongoTemplate.find(any(), eq(JobPost.class))).thenReturn(List.of(jobPost1, jobPost2));

        List<JobPost> result = repository.findManyByIds(ids);

        assertEquals(2, result.size());
        assertEquals("id1", result.get(0).getId());
        assertEquals("id2", result.get(1).getId());
        verify(mongoTemplate, times(1)).find(any(), eq(JobPost.class));
    }

    @Test
    void testUpdateOne() {
        String id = "testId";

        JobPost originalJobPost = new JobPost();
        originalJobPost.setId(id);
        originalJobPost.setTitle("Original Title");

        JobPost updatedJobPost = new JobPost();
        updatedJobPost.setTitle("Updated Title");

        when(mongoTemplate.findOne(any(), eq(JobPost.class))).thenReturn(originalJobPost);
        when(mongoTemplate.updateFirst(any(), any(), eq(JobPost.class)))
            .thenReturn(mock(UpdateResult.class));
        when(mongoTemplate.findById(id, JobPost.class)).thenReturn(updatedJobPost);

        Optional<JobPost> result = repository.updateOne(id, updatedJobPost);

        assertTrue(result.isPresent());
        assertEquals("Updated Title", result.get().getTitle());
        verify(mongoTemplate, times(1)).updateFirst(any(), any(), eq(JobPost.class));
        verify(mongoTemplate, times(1)).findById(id, JobPost.class);
    }

    @Test
    void testDeleteOneById() {
        String id = "testJobPostId";

        DeleteResult deleteResult = mock(DeleteResult.class);
        when(deleteResult.getDeletedCount()).thenReturn(1L);
        when(mongoTemplate.remove(any(), eq(JobPost.class))).thenReturn(deleteResult);

        boolean result = repository.deleteOneById(id);

        assertTrue(result);
        verify(mongoTemplate, times(1)).remove(any(), eq(JobPost.class));
    }

    @Test
    void testExistsByData() {
        JobPost jobPost = new JobPost();
        jobPost.setTitle("Frontend Developer");

        when(mongoTemplate.exists(any(), eq(JobPost.class))).thenReturn(true);

        boolean exists = repository.existsByData(jobPost);

        assertTrue(exists);
        verify(mongoTemplate, times(1)).exists(any(), eq(JobPost.class));
    }
}
