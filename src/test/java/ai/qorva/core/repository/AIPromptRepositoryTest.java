package ai.qorva.core.repository;

import ai.qorva.core.dao.entity.AIPrompt;
import ai.qorva.core.dao.repository.AIPromptRepository;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AIPromptRepositoryTest extends AbstractRepositoryTest<AIPrompt> {

    private AIPromptRepository repository;

    @BeforeEach
    public void init() {
        super.setUp();
        repository = new AIPromptRepository(mongoTemplate);
    }

    @Test
    void testFindOneById() {
        String id = "testId";
        AIPrompt aiPrompt = new AIPrompt();
        aiPrompt.setId(id);

        when(mongoTemplate.findById(id, AIPrompt.class)).thenReturn(aiPrompt);

        Optional<AIPrompt> result = repository.findOneById(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        verify(mongoTemplate, times(1)).findById(id, AIPrompt.class);
    }

    @Test
    void testFindOneByData() {
        AIPrompt aiPrompt = new AIPrompt();
        aiPrompt.setPrompt("Test Prompt");

        when(mongoTemplate.findOne(any(Query.class), eq(AIPrompt.class))).thenReturn(aiPrompt);

        Optional<AIPrompt> result = repository.findOneByData(aiPrompt);

        assertTrue(result.isPresent());
        assertEquals("Test Prompt", result.get().getPrompt());
        verify(mongoTemplate, times(1)).findOne(any(Query.class), eq(AIPrompt.class));
    }

    @Test
    void testCreateOne() {
        AIPrompt aiPrompt = new AIPrompt();
        aiPrompt.setPrompt("Test Prompt");

        when(mongoTemplate.insert(aiPrompt)).thenReturn(aiPrompt);

        AIPrompt result = repository.createOne(aiPrompt);

        assertEquals("Test Prompt", result.getPrompt());
        verify(mongoTemplate, times(1)).insert(aiPrompt);
    }

    @Test
    void testFindMany() {
        AIPrompt prompt1 = new AIPrompt();
        prompt1.setPrompt("Prompt 1");
        AIPrompt prompt2 = new AIPrompt();
        prompt2.setPrompt("Prompt 2");

        when(mongoTemplate.find(any(), eq(AIPrompt.class))).thenReturn(List.of(prompt1, prompt2));
        when(mongoTemplate.count(any(), eq(AIPrompt.class))).thenReturn(2L);

        Page<AIPrompt> result = repository.findMany(0, 2);

        assertEquals(2, result.getTotalElements());
        assertEquals("Prompt 1", result.getContent().get(0).getPrompt());
        assertEquals("Prompt 2", result.getContent().get(1).getPrompt());
        verify(mongoTemplate, times(1)).find(any(), eq(AIPrompt.class));
        verify(mongoTemplate, times(1)).count(any(), eq(AIPrompt.class));
    }

    @Test
    void testFindManyByIds() {
        List<String> ids = List.of("id1", "id2");
        AIPrompt prompt1 = new AIPrompt();
        prompt1.setId("id1");
        AIPrompt prompt2 = new AIPrompt();
        prompt2.setId("id2");

        when(mongoTemplate.find(any(), eq(AIPrompt.class))).thenReturn(List.of(prompt1, prompt2));

        Page<AIPrompt> result = repository.findManyByIds(ids);

        assertEquals(2, result.getTotalElements());
        assertEquals("id1", result.getContent().get(0).getId());
        assertEquals("id2", result.getContent().get(1).getId());
        verify(mongoTemplate, times(1)).find(any(), eq(AIPrompt.class));
    }

    @Test
    void testUpdateOne() {
        String id = "testId";

        // Original AIPrompt
        AIPrompt originalPrompt = new AIPrompt();
        originalPrompt.setId(id);
        originalPrompt.setPrompt("Original Prompt");

        // Updated AIPrompt
        AIPrompt updatedPrompt = new AIPrompt();
        updatedPrompt.setPrompt("Updated Prompt");

        // Mock the findOne and updateFirst operations
        when(mongoTemplate.findOne(any(Query.class), eq(AIPrompt.class))).thenReturn(originalPrompt);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(AIPrompt.class)))
            .thenReturn(Mockito.mock(UpdateResult.class));
        when(mongoTemplate.findById(id, AIPrompt.class)).thenReturn(updatedPrompt);

        // Execute the update operation
        Optional<AIPrompt> result = repository.updateOne(id, updatedPrompt);

        // Assertions
        assertTrue(result.isPresent());
        assertEquals("Updated Prompt", result.get().getPrompt());

        // Verify interactions
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(AIPrompt.class));
        verify(mongoTemplate, times(1)).findById(id, AIPrompt.class);
    }


    @Test
    void testDeleteOneById() {
        String id = "testId";

        when(mongoTemplate.remove(any(Query.class), eq(AIPrompt.class))).thenReturn(Mockito.mock(DeleteResult.class));

        boolean result = repository.deleteOneById(id);

        assertFalse(result);
        verify(mongoTemplate, times(1)).remove(any(Query.class), eq(AIPrompt.class));
    }

    @Test
    void testExistsByData() {
        AIPrompt aiPrompt = new AIPrompt();
        aiPrompt.setPrompt("Test Prompt");

        when(mongoTemplate.exists(any(Query.class), eq(AIPrompt.class))).thenReturn(true);

        boolean exists = repository.existsByData(aiPrompt);

        assertTrue(exists);
        verify(mongoTemplate, times(1)).exists(any(Query.class), eq(AIPrompt.class));
    }
}
