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
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fileTree_returnsTree() throws Exception {
        mockMvc.perform(get("/api/files/tree").param("depth", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.directory").value(true));
    }

    @Test
    void readFile_returnsBadRequestForPathOutsideProject() throws Exception {
        mockMvc.perform(get("/api/files/read").param("path", "application.yml"))
                .andExpect(status().isBadRequest());
    }
}
