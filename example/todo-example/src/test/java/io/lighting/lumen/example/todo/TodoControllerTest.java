package io.lighting.lumen.example.todo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lighting.lumen.example.todo.web.TodoRequest;
import io.lighting.lumen.example.todo.web.TodoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled("Requires APT-generated DAO implementation")
@SpringBootTest
@AutoConfigureMockMvc
class TodoControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("DELETE FROM TODOS");
    }

    @Test
    void crudFlowSupportsPaginationAndFilters() throws Exception {
        TodoResponse first = createTodo("Write docs", null, false);
        TodoResponse second = createTodo("Ship release", "v1.0", true);

        TodoResponse fetched = getTodo(first.id());
        assertEquals("Write docs", fetched.title());
        assertEquals(null, fetched.description());

        JsonNode completedPage = listTodos(1, 10, true);
        assertEquals(1, completedPage.path("items").size());
        assertEquals(1, completedPage.path("total").asInt());

        TodoResponse updated = updateTodo(first.id(), "Write docs", "final README", true);
        assertEquals("final README", updated.description());
        assertEquals(true, updated.completed());

        JsonNode page = listTodos(1, 1, null);
        assertEquals(1, page.path("items").size());
        assertEquals(2, page.path("total").asInt());

        mockMvc.perform(delete("/todos/{id}", first.id()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/todos/{id}", first.id()))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/todos/{id}", second.id()))
            .andExpect(status().isOk());
    }

    @Test
    void validatesRequestsAndPaging() throws Exception {
        TodoRequest request = new TodoRequest(" ", "ignored", false);
        mockMvc.perform(post("/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("title must not be blank"));

        mockMvc.perform(get("/todos")
                .param("page", "0")
                .param("pageSize", "20"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("page must be >= 1"));

        mockMvc.perform(get("/todos")
                .param("page", "1")
                .param("pageSize", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("pageSize must be between 1 and 100"));
    }

    @Test
    void servesTodoPage() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/ui"))
            .andExpect(status().isOk());
    }

    private TodoResponse createTodo(String title, String description, Boolean completed) throws Exception {
        TodoRequest request = new TodoRequest(title, description, completed);
        MvcResult result = mockMvc.perform(post("/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();
        TodoResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            TodoResponse.class
        );
        assertNotNull(response.id());
        return response;
    }

    private TodoResponse getTodo(Long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/todos/{id}", id))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TodoResponse.class);
    }

    private TodoResponse updateTodo(Long id, String title, String description, Boolean completed) throws Exception {
        TodoRequest request = new TodoRequest(title, description, completed);
        MvcResult result = mockMvc.perform(put("/todos/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TodoResponse.class);
    }

    private JsonNode listTodos(int page, int pageSize, Boolean completed) throws Exception {
        var builder = get("/todos")
            .param("page", String.valueOf(page))
            .param("pageSize", String.valueOf(pageSize));
        if (completed != null) {
            builder.param("completed", completed.toString());
        }
        MvcResult result = mockMvc.perform(builder)
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
