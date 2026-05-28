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
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getConfig_returnsConfiguration() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultProjectDir").exists())
                .andExpect(jsonPath("$.proxyBaseUrl").exists())
                .andExpect(jsonPath("$.models").isArray());
    }

    @Test
    void listProjects_returnsProjects() throws Exception {
        mockMvc.perform(get("/api/config/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
