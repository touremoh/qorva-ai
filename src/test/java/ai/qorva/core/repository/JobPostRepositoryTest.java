package ai.qorva.core.repository;

import ai.qorva.core.dao.entity.JobPost;
import ai.qorva.core.dao.repository.JobPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

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
    void testCreateOne() {
        JobPost jobPost = new JobPost();
        jobPost.setTitle("Software Developer");

        when(mongoTemplate.insert(jobPost)).thenReturn(jobPost);

        JobPost result = repository.createOne(jobPost);

        assertNotNull(result);
        assertEquals("Software Developer", result.getTitle());
        verify(mongoTemplate, times(1)).insert(jobPost);
    }

    @Test
    void testFindMany() {
        JobPost post1 = new JobPost();
        post1.setTitle("Developer");
        JobPost post2 = new JobPost();
        post2.setTitle("Analyst");

        when(mongoTemplate.find(any(), eq(JobPost.class))).thenReturn(List.of(post1, post2));
        when(mongoTemplate.count(any(), eq(JobPost.class))).thenReturn(2L);

        Page<JobPost> result = repository.findMany(0, 2);

        assertEquals(2, result.getTotalElements());
        assertEquals("Developer", result.getContent().get(0).getTitle());
        assertEquals("Analyst", result.getContent().get(1).getTitle());
        verify(mongoTemplate, times(1)).find(any(), eq(JobPost.class));
        verify(mongoTemplate, times(1)).count(any(), eq(JobPost.class));
    }

    @Test
    void testFindManyByIds() {
        List<String> ids = List.of("id1", "id2");
        JobPost post1 = new JobPost();
        post1.setId("id1");
        JobPost post2 = new JobPost();
        post2.setId("id2");

        when(mongoTemplate.find(any(), eq(JobPost.class))).thenReturn(List.of(post1, post2));

        Page<JobPost> result = repository.findManyByIds(ids);

        assertEquals(2, result.getTotalElements());
        assertEquals("id1", result.getContent().get(0).getId());
        assertEquals("id2", result.getContent().get(1).getId());
        verify(mongoTemplate, times(1)).find(any(), eq(JobPost.class));
    }
}
