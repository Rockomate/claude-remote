package remote.claude.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listSessions_returnsSessions() throws Exception {
        mockMvc.perform(get("/api/sessions").param("projectDir", "C:\\Users\\MR\\Desktop\\deepseek"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getSession_returnsNotFoundForNonexistent() throws Exception {
        mockMvc.perform(get("/api/sessions/nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSessionMessages_returnsMessages() throws Exception {
        // Get first session ID
        String response = mockMvc.perform(get("/api/sessions").param("projectDir", "C:\\Users\\MR\\Desktop\\deepseek"))
                .andReturn().getResponse().getContentAsString();

        if (response.contains("\"id\"")) {
            String sessionId = response.split("\"id\":\"")[1].split("\"")[0];
            mockMvc.perform(get("/api/sessions/" + sessionId + "/messages").param("projectDir", "C:\\Users\\MR\\Desktop\\deepseek"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }
}
