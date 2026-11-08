package io.github.bred91.moneytransfer.junit.advice;

import static io.github.bred91.moneytransfer.TestIds.id;

import io.github.bred91.moneytransfer.controller.AccountController;
import io.github.bred91.moneytransfer.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void unexpectedError_shouldBeLoggedOnceAndReturnedAsGenericProblem(CapturedOutput output) throws Exception {
        when(accountService.getAccount(id(1)))
                .thenThrow(new DataAccessResourceFailureException("Connection to db:5432 refused"));

        mockMvc.perform(get("/v1/accounts/{id}", id(1)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.instance").value("/v1/accounts/" + id(1)))
                // Internal details stay in the logs and are not exposed to the client
                .andExpect(content().string(not(containsString("db:5432"))));

        assertThat(output.getOut().lines().filter(line -> line.contains(" ERROR ")))
                .singleElement()
                .asString()
                .contains("Unexpected error handling request uri=/v1/accounts/" + id(1));
        assertThat(output).contains("DataAccessResourceFailureException: Connection to db:5432 refused");
    }

    @Test
    void notFound_shouldBeReturnedAsProblemWithoutLoggingAnError(CapturedOutput output) throws Exception {
        when(accountService.getAccount(id(2)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        mockMvc.perform(get("/v1/accounts/{id}", id(2)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Account not found"));

        assertThat(output).doesNotContain(" ERROR ");
    }
}
