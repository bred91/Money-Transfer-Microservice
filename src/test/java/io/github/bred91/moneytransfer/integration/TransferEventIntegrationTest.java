package io.github.bred91.moneytransfer.integration;

import io.github.bred91.moneytransfer.dto.TransferEventDTO;
import io.github.bred91.moneytransfer.entity.AccountEntity;
import io.github.bred91.moneytransfer.entity.TransferLogEntity;
import io.github.bred91.moneytransfer.enumeration.AccountStatusEnum;
import io.github.bred91.moneytransfer.enumeration.TransactionStatusEnum;
import io.github.bred91.moneytransfer.repository.AccountRepository;
import io.github.bred91.moneytransfer.repository.TransferLogRepository;
import io.github.bred91.moneytransfer.service.TransferProcessingService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
@DisplayName("TransferEvent Integration Tests")
class TransferEventIntegrationTest {

    private static final String TOPIC = "money-transfers";
    private static final String DEAD_LETTER_TOPIC = "money-transfers.DLT";
    private static final int MAX_RETRIES = 2;

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.4.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.docker.compose.enabled", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("app.kafka.topic.transfer", () -> TOPIC);
        registry.add("app.kafka.topic.transfer-dead-letter", () -> DEAD_LETTER_TOPIC);
        // Short back-off, so that the error handling scenarios complete quickly
        registry.add("app.kafka.retry.initial-interval", () -> "100ms");
        registry.add("app.kafka.retry.max-interval", () -> "100ms");
        registry.add("app.kafka.retry.max-retries", () -> MAX_RETRIES);
    }

    @Autowired
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferLogRepository transferLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Calls the real service unless a test stubs it to simulate a failure
    @MockitoSpyBean
    private TransferProcessingService processingService;

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

    private static TransferEventDTO transfer(String transactionId, UUID fromId, UUID toId, String amount) {
        TransferEventDTO event = new TransferEventDTO();
        event.setTransactionId(transactionId);
        event.setFromAccountId(fromId);
        event.setToAccountId(toId);
        event.setAmount(new BigDecimal(amount));
        return event;
    }

    private BigDecimal balanceOf(AccountEntity account) {
        return accountRepository.findById(account.getId()).orElseThrow().getBalance();
    }

    private void sendTransferEvent(TransferEventDTO event) throws Exception {
        send(objectMapper.writeValueAsBytes(event));
    }

    /**
     * Publishes a record with a unique key, and returns the key to find the record on the dead-letter topic.
     */
    private String send(byte @Nullable [] value) throws Exception {
        String key = UUID.randomUUID().toString();
        kafkaTemplate.send(TOPIC, key, value).get(5, TimeUnit.SECONDS);
        return key;
    }

    /**
     * Waits until the record published with the given key reaches the dead-letter topic.
     */
    private static ConsumerRecord<String, byte[]> awaitDeadLetter(String key) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dead-letter-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(DEAD_LETTER_TOPIC));
            AtomicReference<ConsumerRecord<String, byte[]>> deadLetter = new AtomicReference<>();
            await()
                    .timeout(Duration.ofSeconds(15))
                    .pollInSameThread()
                    .until(() -> {
                        consumer.poll(Duration.ofMillis(200)).forEach(record -> {
                            if (key.equals(record.key())) {
                                deadLetter.set(record);
                            }
                        });
                        return deadLetter.get() != null;
                    });
            return deadLetter.get();
        }
    }

    private static @Nullable String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }

    /**
     * Asserts that the record was dead-lettered unchanged because of the given failure, without being processed.
     */
    private void assertDeadLettered(String key, byte @Nullable [] value, Class<? extends Exception> failure) {
        ConsumerRecord<String, byte[]> deadLetter = awaitDeadLetter(key);

        assertArrayEquals(value, deadLetter.value(), "The original record should be dead-lettered unchanged");
        assertEquals(TOPIC, header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC));
        assertEquals(failure.getName(), header(deadLetter, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN));
        assertTrue(transferLogRepository.findAll().isEmpty(), "No transfer log should be created");
    }

    @Nested
    @DisplayName("Valid Transfer Scenarios")
    class ValidTransferScenarios {

        @Test
        @DisplayName("Should process valid transfer successfully")
        void shouldProcessValidTransferSuccessfully() throws Exception {
            AccountEntity fromAccount = createAccount("John", "Doe", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Jane", "Smith", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);

            sendTransferEvent(transfer("tx-001", fromAccount.getId(), toAccount.getId(), "100.00"));

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        assertEquals(new BigDecimal("900.00"), balanceOf(fromAccount));
                        assertEquals(new BigDecimal("600.00"), balanceOf(toAccount));
                        assertTrue(transferLogRepository.findByTransactionId("tx-001").isPresent());
                    });
        }

        @Test
        @DisplayName("Should handle transfer with fractional cents")
        void shouldHandleTransferWithFractionalCents() throws Exception {
            AccountEntity fromAccount = createAccount("Alice", "Johnson", new BigDecimal("1999.99"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Bob", "Williams", new BigDecimal("0.01"), AccountStatusEnum.ACTIVE);

            sendTransferEvent(transfer("tx-002", fromAccount.getId(), toAccount.getId(), "1999.98"));

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        assertEquals(new BigDecimal("0.01"), balanceOf(fromAccount));
                        assertEquals(new BigDecimal("1999.99"), balanceOf(toAccount));
                    });
        }

        @Test
        @DisplayName("Should be idempotent when processing duplicate transactions")
        void shouldBeIdempotentWhenProcessingDuplicateTransactions() throws Exception {
            AccountEntity fromAccount = createAccount("Charlie", "Brown", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Diana", "Prince", new BigDecimal("200.00"), AccountStatusEnum.ACTIVE);

            TransferEventDTO event = transfer("tx-003", fromAccount.getId(), toAccount.getId(), "50.00");

            sendTransferEvent(event);

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() ->
                            assertTrue(transferLogRepository.findByTransactionId("tx-003").isPresent())
                    );

            BigDecimal firstBalance = balanceOf(fromAccount);

            sendTransferEvent(event);

            Thread.sleep(1000);

            assertEquals(firstBalance, balanceOf(fromAccount), "Balance should not change on duplicate");
        }
    }

    @Nested
    @DisplayName("Invalid Transfer Scenarios")
    class InvalidTransferScenarios {

        @Test
        @DisplayName("Should dead-letter a tombstone record")
        void shouldDeadLetterTombstoneRecord() throws Exception {
            String key = send(null);

            assertDeadLettered(key, null, MethodArgumentNotValidException.class);
        }

        @Test
        @DisplayName("Should dead-letter a malformed JSON payload")
        void shouldDeadLetterMalformedJsonPayload() throws Exception {
            byte[] payload = "not a transfer event".getBytes(UTF_8);

            String key = send(payload);

            assertDeadLettered(key, payload, ConversionException.class);
        }

        @Test
        @DisplayName("Should dead-letter a transfer with a malformed account ID")
        void shouldDeadLetterTransferWithMalformedAccountId() throws Exception {
            byte[] payload = """
                    {"transactionId":"tx-invalid-id","fromAccountId":"not-a-uuid","toAccountId":"%s","amount":100.00}
                    """.formatted(UUID.randomUUID()).getBytes(UTF_8);

            String key = send(payload);

            assertDeadLettered(key, payload, ConversionException.class);
        }

        @Test
        @DisplayName("Should dead-letter a transfer with blank transaction ID")
        void shouldDeadLetterTransferWithBlankTransactionId() throws Exception {
            AccountEntity fromAccount = createAccount("Eve", "Miller", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Frank", "Davis", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);
            byte[] payload = objectMapper.writeValueAsBytes(transfer("", fromAccount.getId(), toAccount.getId(), "100.00"));

            String key = send(payload);

            assertDeadLettered(key, payload, MethodArgumentNotValidException.class);
        }

        @Test
        @DisplayName("Should dead-letter a transfer with zero amount")
        void shouldDeadLetterTransferWithZeroAmount() throws Exception {
            AccountEntity fromAccount = createAccount("Grace", "Wilson", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Henry", "Moore", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);
            byte[] payload = objectMapper.writeValueAsBytes(transfer("tx-zero", fromAccount.getId(), toAccount.getId(), "0"));

            String key = send(payload);

            assertDeadLettered(key, payload, MethodArgumentNotValidException.class);
        }

        @Test
        @DisplayName("Should dead-letter a transfer with negative amount")
        void shouldDeadLetterTransferWithNegativeAmount() throws Exception {
            AccountEntity fromAccount = createAccount("Ivy", "Taylor", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Jack", "Anderson", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);
            byte[] payload = objectMapper.writeValueAsBytes(transfer("tx-negative", fromAccount.getId(), toAccount.getId(), "-50.00"));

            String key = send(payload);

            assertDeadLettered(key, payload, MethodArgumentNotValidException.class);
        }

        // Regression test: the NUMERIC(19, 2) columns used to round 100.00 - 0.005 to 100.00 and 50.00 + 0.005 to 50.01
        @Test
        @DisplayName("Should dead-letter a transfer with more than 2 decimal places, without creating money")
        void shouldDeadLetterTransferWithSubCentAmount() throws Exception {
            AccountEntity fromAccount = createAccount("Yara", "Hill", new BigDecimal("100.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Zack", "Green", new BigDecimal("50.00"), AccountStatusEnum.ACTIVE);
            byte[] payload = objectMapper.writeValueAsBytes(transfer("tx-sub-cent", fromAccount.getId(), toAccount.getId(), "0.005"));

            String key = send(payload);

            assertDeadLettered(key, payload, MethodArgumentNotValidException.class);
            assertEquals(new BigDecimal("100.00"), balanceOf(fromAccount));
            assertEquals(new BigDecimal("50.00"), balanceOf(toAccount));
        }

        @Test
        @DisplayName("Should dead-letter a transfer when source and destination are the same")
        void shouldDeadLetterTransferWhenSourceAndDestinationAreSame() throws Exception {
            AccountEntity account = createAccount("Kelly", "Jackson", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            byte[] payload = objectMapper.writeValueAsBytes(transfer("tx-same-account", account.getId(), account.getId(), "100.00"));

            String key = send(payload);

            assertDeadLettered(key, payload, MethodArgumentNotValidException.class);
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

            sendTransferEvent(transfer("tx-source-inactive", fromAccount.getId(), toAccount.getId(), "100.00"));

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

            sendTransferEvent(transfer("tx-dest-inactive", fromAccount.getId(), toAccount.getId(), "100.00"));

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

            sendTransferEvent(transfer("tx-insufficient", fromAccount.getId(), toAccount.getId(), "100.00"));

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        assertEquals(new BigDecimal("50.00"), balanceOf(fromAccount));
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

            sendTransferEvent(transfer("tx-concurrent-1", fromAccount1.getId(), toAccount.getId(), "500.00"));
            sendTransferEvent(transfer("tx-concurrent-2", fromAccount2.getId(), toAccount.getId(), "300.00"));

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> assertEquals(new BigDecimal("800.00"), balanceOf(toAccount)));
        }

        @Test
        @DisplayName("Should reject transfer when account does not exist")
        void shouldRejectTransferWhenAccountNotFound() throws Exception {
            AccountEntity fromAccount = createAccount("Uma", "Young", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);

            sendTransferEvent(transfer("tx-not-found", fromAccount.getId(), UUID.randomUUID(), "100.00"));

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() ->
                            assertEquals(new BigDecimal("1000.00"), balanceOf(fromAccount),
                                    "Source balance should not change when destination account not found"));
        }
    }

    @Nested
    @DisplayName("Error Handling Scenarios")
    class ErrorHandlingScenarios {

        @Test
        @DisplayName("Should retry a transfer that fails with a transient error")
        void shouldRetryTransferThatFailsWithTransientError() throws Exception {
            AccountEntity fromAccount = createAccount("Amy", "Baker", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Ben", "Carter", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);
            doThrow(new TransientDataAccessResourceException("Database unavailable"))
                    .doCallRealMethod()
                    .when(processingService).processTransfer(eq("tx-transient"), any(), any(), any());

            sendTransferEvent(transfer("tx-transient", fromAccount.getId(), toAccount.getId(), "100.00"));

            await()
                    .timeout(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        assertEquals(new BigDecimal("900.00"), balanceOf(fromAccount));
                        assertEquals(new BigDecimal("600.00"), balanceOf(toAccount));
                    });
            TransferLogEntity log = transferLogRepository.findByTransactionId("tx-transient").orElseThrow();
            assertEquals(TransactionStatusEnum.SUCCESS, log.getStatus());
            verify(processingService, times(2)).processTransfer(eq("tx-transient"), any(), any(), any());
        }

        @Test
        @DisplayName("Should dead-letter a transfer once the retries are exhausted")
        void shouldDeadLetterTransferOnceRetriesAreExhausted() throws Exception {
            AccountEntity fromAccount = createAccount("Cora", "Diaz", new BigDecimal("1000.00"), AccountStatusEnum.ACTIVE);
            AccountEntity toAccount = createAccount("Dan", "Evans", new BigDecimal("500.00"), AccountStatusEnum.ACTIVE);
            doThrow(new TransientDataAccessResourceException("Database unavailable"))
                    .when(processingService).processTransfer(eq("tx-exhausted"), any(), any(), any());
            byte[] payload = objectMapper.writeValueAsBytes(transfer("tx-exhausted", fromAccount.getId(), toAccount.getId(), "100.00"));

            String key = send(payload);

            assertDeadLettered(key, payload, TransientDataAccessResourceException.class);
            verify(processingService, times(MAX_RETRIES + 1)).processTransfer(eq("tx-exhausted"), any(), any(), any());
            assertEquals(new BigDecimal("1000.00"), balanceOf(fromAccount));
            assertEquals(new BigDecimal("500.00"), balanceOf(toAccount));
        }
    }
}
