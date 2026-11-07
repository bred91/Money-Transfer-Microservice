package io.github.bred91.moneytransfer.junit.controller;

import static io.github.bred91.moneytransfer.TestIds.id;

import io.github.bred91.moneytransfer.controller.AccountController;
import io.github.bred91.moneytransfer.dto.AccountResponseDTO;
import io.github.bred91.moneytransfer.dto.CreateAccountRequestDTO;
import io.github.bred91.moneytransfer.enumeration.AccountStatusEnum;
import io.github.bred91.moneytransfer.service.AccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AccountController accountController;

    @AfterEach
    void cleanupRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void create_shouldReturn201WithLocationAndBody() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/accounts");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        CreateAccountRequestDTO req = new CreateAccountRequestDTO();
        req.setOwnerName("John");
        req.setOwnerSurname("Doe");
        req.setInitialBalance(new BigDecimal("100.00"));

        AccountResponseDTO created = AccountResponseDTO.builder()
                .id(id(1))
                .ownerName("John")
                .ownerSurname("Doe")
                .status(AccountStatusEnum.ACTIVE)
                .balance(new BigDecimal("100.00"))
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(accountService.createAccount(req)).thenReturn(created);

        ResponseEntity<AccountResponseDTO> response = accountController.create(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("http://localhost/v1/accounts/" + id(1));
        assertThat(response.getBody()).isEqualTo(created);

        ArgumentCaptor<CreateAccountRequestDTO> captor = ArgumentCaptor.forClass(CreateAccountRequestDTO.class);
        verify(accountService).createAccount(captor.capture());
        assertThat(captor.getValue().getOwnerName()).isEqualTo("John");
        assertThat(captor.getValue().getOwnerSurname()).isEqualTo("Doe");
        assertThat(captor.getValue().getInitialBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void get_shouldReturn200WithBody() {
        AccountResponseDTO dto = AccountResponseDTO.builder()
                .id(id(2))
                .ownerName("Alice")
                .ownerSurname("Smith")
                .status(AccountStatusEnum.ACTIVE)
                .balance(new BigDecimal("100.00"))
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .build();

        when(accountService.getAccount(id(2))).thenReturn(dto);

        ResponseEntity<AccountResponseDTO> response = accountController.get(id(2));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(dto);
        verify(accountService).getAccount(id(2));
    }

    @Test
    void get_shouldThrowNotFoundWhenAccountDoesNotExist() {
        when(accountService.getAccount(id(999)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        assertThatThrownBy(() -> accountController.get(id(999)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND")
                .hasMessageContaining("Account not found");

        verify(accountService).getAccount(id(999));
    }

    @Test
    void list_shouldReturn200WithBody() {
        List<AccountResponseDTO> list = List.of(
                AccountResponseDTO.builder().id(id(1)).build(),
                AccountResponseDTO.builder().id(id(2)).build()
        );

        when(accountService.listAccounts()).thenReturn(list);

        ResponseEntity<List<AccountResponseDTO>> response = accountController.list();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(list);
        verify(accountService).listAccounts();
    }
}