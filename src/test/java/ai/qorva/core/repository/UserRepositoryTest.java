package ai.qorva.core.repository;

import ai.qorva.core.dao.entity.User;
import ai.qorva.core.dao.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserRepositoryTest extends AbstractRepositoryTest<User> {

    private UserRepository repository;

    @BeforeEach
    public void init() {
        super.setUp();
        repository = new UserRepository(mongoTemplate);
    }

    @Test
    void testFindOneById() {
        String id = "testUserId";
        User user = new User();
        user.setId(id);

        when(mongoTemplate.findById(id, User.class)).thenReturn(user);

        Optional<User> result = repository.findOneById(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        verify(mongoTemplate, times(1)).findById(id, User.class);
    }

    @Test
    void testFindOneByData() {
        User user = new User();
        user.setEmail("test@example.com");

        when(mongoTemplate.findOne(any(), eq(User.class))).thenReturn(user);

        Optional<User> result = repository.findOneByData(user);

        assertTrue(result.isPresent());
        assertEquals("test@example.com", result.get().getEmail());
        verify(mongoTemplate, times(1)).findOne(any(), eq(User.class));
    }

    @Test
    void testCreateOne() {
        User user = new User();
        user.setEmail("create@example.com");

        when(mongoTemplate.insert(user)).thenReturn(user);

        User result = repository.createOne(user);

        assertNotNull(result);
        assertEquals("create@example.com", result.getEmail());
        verify(mongoTemplate, times(1)).insert(user);
    }

    @Test
    void testFindMany() {
        User user1 = new User();
        user1.setEmail("user1@example.com");
        User user2 = new User();
        user2.setEmail("user2@example.com");

        when(mongoTemplate.find(any(), eq(User.class))).thenReturn(List.of(user1, user2));

        List<User> result = repository.findMany(0, 2);

        assertEquals(2, result.size());
        assertEquals("user1@example.com", result.get(0).getEmail());
        assertEquals("user2@example.com", result.get(1).getEmail());
        verify(mongoTemplate, times(1)).find(any(), eq(User.class));
    }

    @Test
    void testFindManyByIds() {
        List<String> ids = List.of("id1", "id2");
        User user1 = new User();
        user1.setId("id1");
        User user2 = new User();
        user2.setId("id2");

        when(mongoTemplate.find(any(), eq(User.class))).thenReturn(List.of(user1, user2));

        List<User> result = repository.findManyByIds(ids);

        assertEquals(2, result.size());
        assertEquals("id1", result.get(0).getId());
        assertEquals("id2", result.get(1).getId());
        verify(mongoTemplate, times(1)).find(any(), eq(User.class));
    }

    @Test
    void testUpdateOne() {
        String id = "testUserId";

        User originalUser = new User();
        originalUser.setId(id);
        originalUser.setEmail("original@example.com");

        User updatedUser = new User();
        updatedUser.setEmail("updated@example.com");

        when(mongoTemplate.findOne(any(), eq(User.class))).thenReturn(originalUser);
        when(mongoTemplate.updateFirst(any(), any(), eq(User.class)))
            .thenReturn(mock(UpdateResult.class));
        when(mongoTemplate.findById(id, User.class)).thenReturn(updatedUser);

        Optional<User> result = repository.updateOne(id, updatedUser);

        assertTrue(result.isPresent());
        assertEquals("updated@example.com", result.get().getEmail());
        verify(mongoTemplate, times(1)).updateFirst(any(), any(), eq(User.class));
        verify(mongoTemplate, times(1)).findById(id, User.class);
    }

    @Test
    void testDeleteOneById() {
        String id = "testUserId";

        DeleteResult deleteResult = mock(DeleteResult.class);
        when(deleteResult.getDeletedCount()).thenReturn(1L);
        when(mongoTemplate.remove(any(), eq(User.class))).thenReturn(deleteResult);

        boolean result = repository.deleteOneById(id);

        assertTrue(result);
        verify(mongoTemplate, times(1)).remove(any(), eq(User.class));
    }

    @Test
    void testExistsByData() {
        User user = new User();
        user.setEmail("exists@example.com");

        when(mongoTemplate.exists(any(), eq(User.class))).thenReturn(true);

        boolean exists = repository.existsByData(user);

        assertTrue(exists);
        verify(mongoTemplate, times(1)).exists(any(), eq(User.class));
    }
}
