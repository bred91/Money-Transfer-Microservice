package io.github.bred91.moneytransfer.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.github.bred91.moneytransfer.enumeration.AccountStatusEnum;
import io.github.bred91.moneytransfer.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Account Integration Tests")
public class AccountIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.docker.compose.enabled", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void cleanDatabase() {
        // for a clean state
        accountRepository.deleteAll();
    }

    @Test
    void fullFlow_listCreateRelistGetSingle() throws Exception {
        // 1) Read list first: expected empty
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 2) Create one account
        String createResponse = mockMvc.perform(post("/api/v1/accounts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "Mario",
                                  "ownerSurname": "Rossi",
                                  "initialBalance": 123.45
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerName").value("Mario"))
                .andExpect(jsonPath("$.ownerSurname").value("Rossi"))
                .andExpect(jsonPath("$.balance").value(123.45))
                .andExpect(jsonPath("$.status").value(AccountStatusEnum.ACTIVE.toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.lastModifiedAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract generated ID from create response
        JsonNode json = objectMapper.readTree(createResponse);
        String id = json.get("id").asString();

        // 3) Read list again: expected one account
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].ownerName").value("Mario"))
                .andExpect(jsonPath("$[0].ownerSurname").value("Rossi"))
                .andExpect(jsonPath("$[0].balance").value(123.45))
                .andExpect(jsonPath("$[0].status").value(AccountStatusEnum.ACTIVE.toString()));

        // 4) Get the single account by ID
        mockMvc.perform(get("/api/v1/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.ownerName").value("Mario"))
                .andExpect(jsonPath("$.ownerSurname").value("Rossi"))
                .andExpect(jsonPath("$.balance").value(123.45))
                .andExpect(jsonPath("$.status").value(AccountStatusEnum.ACTIVE.toString()));
    }

    @Test
    void create_shouldReturnProblemDetailWithFieldErrors_whenPayloadIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "",
                                  "ownerSurname": "Rossi",
                                  "initialBalance": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.instance").value("/api/v1/accounts"))
                .andExpect(jsonPath("$.errors.ownerName").value("Name is required"))
                .andExpect(jsonPath("$.errors.initialBalance").value("Initial balance must not be negative"));
    }

    @Test
    void get_shouldReturnProblemDetail_whenAccountDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Account not found"));
    }

    @Test
    void get_shouldReturnBadRequest_whenIdIsNotAUuid() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void list_shouldReturnBadRequest_whenApiVersionIsNotSupported() throws Exception {
        mockMvc.perform(get("/api/v2/accounts"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void openApiDocument_shouldDescribeTheVersionedApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Money Transfer Service API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.paths['/api/v1/accounts'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{id}'].get.responses['404']").exists());
    }
}
