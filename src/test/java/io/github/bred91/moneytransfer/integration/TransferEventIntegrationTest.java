package io.github.bred91.moneytransfer.integration;

import tools.jackson.databind.ObjectMapper;
import io.github.bred91.moneytransfer.dto.TransferEventDTO;
import io.github.bred91.moneytransfer.entity.AccountEntity;
import io.github.bred91.moneytransfer.enumeration.AccountStatusEnum;
import io.github.bred91.moneytransfer.repository.AccountRepository;
import io.github.bred91.moneytransfer.repository.TransferLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@DisplayName("TransferEvent Integration Tests")
class TransferEventIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.4.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.docker.compose.enabled", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("app.kafka.topic.transfer", () -> "money-transfers");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferLogRepository transferLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        transferLogRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private AccountEntity createAccount(String name, String surname, BigDecimal balance, AccountStatusEnum status) {
        return accountRepository.save(
                AccountEntity.builder()
                        .ownerName(name)
                        .ownerSurname(surname)
                        .balance(balance)
                        .status(status)
                        .build()
        );
    }

    private void sendTransferEvent(TransferEventDTO dto) throws Exception {
        kafkaTemplate.send(
                MessageBuilder
                        .withPayload(objectMapper.writeValueAsString(dto))
                        .setHeader(KafkaHeaders.TOPIC, "money-transfers")
                        .build()
        ).get(5, TimeUnit.SECONDS);
    }

    @Nested
    @DisplayName("Valid Transfer Scenarios")
    class ValidTransferScenarios {

        @Test
        @DisplayName("Should process valid transfer successfully")
        void shouldProcessValidTransferSuccessfully() throws Exception {
            AccountEntity fromAccount = createAccount("John", "Doe", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Jane", "Smith", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);

            TransferEventDTO event = new TransferEventDTO();
            event.setTransactionId("tx-001");
            event.setFromAccountId(fromAccount.getId());
            event.setToAccountId(toAccount.getId());
            event.setAmount(new BigDecimal("100.00"));

            sendTransferEvent(event);

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        AccountEntity updatedFrom = accountRepository.findById(fromAccount.getId()).orElseThrow();
                        AccountEntity updatedTo = accountRepository.findById(toAccount.getId()).orElseThrow();

                        assertEquals(new BigDecimal("900.00"), updatedFrom.getBalance());
                        assertEquals(new BigDecimal("600.00"), updatedTo.getBalance());
                        assertTrue(transferLogRepository.findByTransactionId("tx-001").isPresent());
                    });
        }

        @Test
        @DisplayName("Should handle transfer with fractional cents")
        void shouldHandleTransferWithFractionalCents() throws Exception {
            AccountEntity fromAccount = createAccount("Alice", "Johnson", new BigDecimal("1999.99"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Bob", "Williams", new BigDecimal("0.01"), AccountStatusEnum.ACTIVE);

            TransferEventDTO event = new TransferEventDTO();
            event.setTransactionId("tx-002");
            event.setFromAccountId(fromAccount.getId());
            event.setToAccountId(toAccount.getId());
            event.setAmount(new BigDecimal("1999.98"));

            sendTransferEvent(event);

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        AccountEntity updatedFrom = accountRepository.findById(fromAccount.getId()).orElseThrow();
                        AccountEntity updatedTo = accountRepository.findById(toAccount.getId()).orElseThrow();

                        assertEquals(new BigDecimal("0.01"), updatedFrom.getBalance());
                        assertEquals(new BigDecimal("1999.99"), updatedTo.getBalance());
                    });
        }

        @Test
        @DisplayName("Should be idempotent when processing duplicate transactions")
        void shouldBeIdempotentWhenProcessingDuplicateTransactions() throws Exception {
            AccountEntity fromAccount = createAccount("Charlie", "Brown", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Diana", "Prince", new BigDecimal("200.00"), AccountStatusEnum.ACTIVE);

            TransferEventDTO event = new TransferEventDTO();
            event.setTransactionId("tx-003");
            event.setFromAccountId(fromAccount.getId());
            event.setToAccountId(toAccount.getId());
            event.setAmount(new BigDecimal("50.00"));

            sendTransferEvent(event);

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() ->
                            assertTrue(transferLogRepository.findByTransactionId("tx-003").isPresent())
                    );

            AccountEntity firstSnapshot = accountRepository.findById(fromAccount.getId()).orElseThrow();
            BigDecimal firstBalance = firstSnapshot.getBalance();

            sendTransferEvent(event);

            Thread.sleep(1000);

            AccountEntity secondSnapshot = accountRepository.findById(fromAccount.getId()).orElseThrow();
            assertEquals(firstBalance, secondSnapshot.getBalance(), "Balance should not change on duplicate");
        }
    }

    @Nested
    @DisplayName("Invalid Transfer Scenarios")
    class InvalidTransferScenarios {

        @Test
        @DisplayName("Should skip null payload (tombstone)")
        void shouldSkipNullPayload() throws Exception {
            kafkaTemplate.send(
                    MessageBuilder
                            .withPayload("")
                            .setHeader(KafkaHeaders.TOPIC, "money-transfers")
                            .build()
            ).get(5, TimeUnit.SECONDS);

            Thread.sleep(1000);

            assertTrue(transferLogRepository.findAll().isEmpty(), "No transfer log should be created");
        }

        @Test
        @DisplayName("Should reject transfer with blank transaction ID")
        void shouldRejectTransferWithBlankTransactionId() throws Exception {
            AccountEntity fromAccount = createAccount("Eve", "Miller", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Frank", "Davis", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);

            TransferEventDTO event = new TransferEventDTO();
            event.setTransactionId("");
            event.setFromAccountId(fromAccount.getId());
            event.setToAccountId(toAccount.getId());
            event.setAmount(new BigDecimal("100.00"));

            sendTransferEvent(event);

            Thread.sleep(1000);

            assertTrue(transferLogRepository.findAll().isEmpty(), "No transfer log should be created");
        }

        @Test
        @DisplayName("Should reject transfer with zero amount")
        void shouldRejectTransferWithZeroAmount() throws Exception {
            AccountEntity fromAccount = createAccount("Grace", "Wilson", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Henry", "Moore", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);

            TransferEventDTO event = new TransferEventDTO();
            event.setTransactionId("tx-zero");
            event.setFromAccountId(fromAccount.getId());
            event.setToAccountId(toAccount.getId());
            event.setAmount(BigDecimal.ZERO);

            sendTransferEvent(event);

            Thread.sleep(1000);

            assertTrue(transferLogRepository.findAll().isEmpty(), "No transfer log should be created");
        }

        @Test
        @DisplayName("Should reject transfer with negative amount")
        void shouldRejectTransferWithNegativeAmount() throws Exception {
            AccountEntity fromAccount = createAccount("Ivy", "Taylor", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Jack", "Anderson", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);

            TransferEventDTO event = new TransferEventDTO();
            event.setTransactionId("tx-negative");
            event.setFromAccountId(fromAccount.getId());
            event.setToAccountId(toAccount.getId());
            event.setAmount(new BigDecimal("-50.00"));

            sendTransferEvent(event);

            Thread.sleep(1000);

            assertTrue(transferLogRepository.findAll().isEmpty(), "No transfer log should be created");
        }

        @Test
        @DisplayName("Should reject transfer with invalid account IDs")
        void shouldRejectTransferWithInvalidAccountIds() throws Exception {
            TransferEventDTO event = new TransferEventDTO();
            event.setTransactionId("tx-invalid-id");
            event.setFromAccountId(0L);
            event.setToAccountId(2L);
            event.setAmount(new BigDecimal("100.00"));

            sendTransferEvent(event);

            Thread.sleep(1000);

            assertTrue(transferLogRepository.findAll().isEmpty(), "No transfer log should be created");
        }

        @Test
        @DisplayName("Should reject transfer when source and destination are the same")
        void shouldRejectTransferWhenSourceAndDestinationAreSame() throws Exception {
            AccountEntity account = createAccount("Kelly", "Jackson", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);

            TransferEventDTO event = new TransferEventDTO();
            event.setTransactionId("tx-same-account");
            event.setFromAccountId(account.getId());
            event.setToAccountId(account.getId());
            event.setAmount(new BigDecimal("100.00"));

            sendTransferEvent(event);

            Thread.sleep(1000);

            assertTrue(transferLogRepository.findAll().isEmpty(), "No transfer log should be created");
        }
    }

    @Nested
    @DisplayName("Business Logic Scenarios")
    class BusinessLogicScenarios {

        @Test
        @DisplayName("Should reject transfer when source account is inactive")
        void shouldRejectTransferWhenSourceAccountIsInactive() throws Exception {
            AccountEntity fromAccount = createAccount("Laura", "White", new BigDecimal("1000.00"), AccountStatusEnum.FROZEN);
            AccountEntity toAccount = createAccount("Mike", "Harris", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);

            TransferEventDTO event = new TransferEventDTO();
            event.setTransactionId("tx-source-inactive");
            event.setFromAccountId(fromAccount.getId());
            event.setToAccountId(toAccount.getId());
            event.setAmount(new BigDecimal("100.00"));

            sendTransferEvent(event);

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() ->
                            assertTrue(
                                    transferLogRepository.findByTransactionId("tx-source-inactive").isPresent(),
                                    "Transfer log should be created with FAILED status"
                            )
                    );
        }

        @Test
        @DisplayName("Should reject transfer when destination account is inactive")
        void shouldRejectTransferWhenDestinationAccountIsInactive() throws Exception {
            AccountEntity fromAccount = createAccount("Nancy", "Martin", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Oscar", "Garcia", new BigDecimal("500.00"), AccountStatusEnum.CLOSED);

            TransferEventDTO event = new TransferEventDTO();
            event.setTransactionId("tx-dest-inactive");
            event.setFromAccountId(fromAccount.getId());
            event.setToAccountId(toAccount.getId());
            event.setAmount(new BigDecimal("100.00"));

            sendTransferEvent(event);

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() ->
                            assertTrue(
                                    transferLogRepository.findByTransactionId("tx-dest-inactive").isPresent(),
                                    "Transfer log should be created with FAILED status"
                            )
                    );
        }

        @Test
        @DisplayName("Should reject transfer when source account has insufficient funds")
        void shouldRejectTransferWhenInsufficientFunds() throws Exception {
            AccountEntity fromAccount = createAccount("Paula", "Rodriguez", new BigDecimal("50.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Quinn", "Martinez", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);

            TransferEventDTO event = new TransferEventDTO();
            event.setTransactionId("tx-insufficient");
            event.setFromAccountId(fromAccount.getId());
            event.setToAccountId(toAccount.getId());
            event.setAmount(new BigDecimal("100.00"));

            sendTransferEvent(event);

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        AccountEntity unchanged = accountRepository.findById(fromAccount.getId()).orElseThrow();
                        assertEquals(new BigDecimal("50.00"), unchanged.getBalance());
                        assertTrue(
                                transferLogRepository.findByTransactionId("tx-insufficient").isPresent(),
                                "Transfer log should be created with FAILED status"
                        );
                    });
        }

        @Test
        @DisplayName("Should handle concurrent transfers to same account")
        void shouldHandleConcurrentTransfersToSameAccount() throws Exception {
            AccountEntity fromAccount1 = createAccount("Rachel", "Lee", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            AccountEntity fromAccount2 = createAccount("Steve", "Clark", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Tina", "Lewis", new BigDecimal("0.00"), AccountStatusEnum.ACTIVE);

            TransferEventDTO event1 = new TransferEventDTO();
            event1.setTransactionId("tx-concurrent-1");
            event1.setFromAccountId(fromAccount1.getId());
            event1.setToAccountId(toAccount.getId());
            event1.setAmount(new BigDecimal("500.00"));

            TransferEventDTO event2 = new TransferEventDTO();
            event2.setTransactionId("tx-concurrent-2");
            event2.setFromAccountId(fromAccount2.getId());
            event2.setToAccountId(toAccount.getId());
            event2.setAmount(new BigDecimal("300.00"));

            sendTransferEvent(event1);
            sendTransferEvent(event2);

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        AccountEntity updated = accountRepository.findById(toAccount.getId()).orElseThrow();
                        assertEquals(new BigDecimal("800.00"), updated.getBalance());
                    });
        }

        @Test
        @DisplayName("Should reject transfer when account does not exist")
        void shouldRejectTransferWhenAccountNotFound() throws Exception {
            AccountEntity fromAccount = createAccount("Uma", "Young", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);

            TransferEventDTO event = new TransferEventDTO();
            event.setTransactionId("tx-not-found");
            event.setFromAccountId(fromAccount.getId());
            event.setToAccountId(9999L);
            event.setAmount(new BigDecimal("100.00"));

            sendTransferEvent(event);

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        AccountEntity unchanged = accountRepository.findById(fromAccount.getId()).orElseThrow();
                        assertEquals(new BigDecimal("1000.00"), unchanged.getBalance(), "Source balance should not change when destination account not found");
                    });
        }
    }
}