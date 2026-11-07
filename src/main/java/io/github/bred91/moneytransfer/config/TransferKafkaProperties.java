package io.github.bred91.moneytransfer.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Kafka settings of the transfer consumer ({@code app.kafka.*}).
 *
 * @param topic topics the transfer events are read from and dead-lettered to
 * @param retry retry policy for transfers that fail with a transient error
 */
@Validated
@ConfigurationProperties("app.kafka")
public record TransferKafkaProperties(@NotNull @Valid Topic topic, @NotNull @Valid Retry retry) {

    /**
     * @param transfer           topic the transfer events are consumed from
     * @param transferDeadLetter topic the transfer events that cannot be processed are forwarded to
     */
    public record Topic(@NotBlank String transfer, @NotBlank String transferDeadLetter) {
    }

    /**
     * Exponential back-off between attempts; once {@code maxRetries} is exhausted the event is dead-lettered.
     *
     * @param initialInterval delay before the first retry
     * @param multiplier      factor applied to the delay after each retry
     * @param maxInterval     upper bound of the delay between two attempts
     * @param maxRetries      retries after the first failed attempt
     */
    public record Retry(@NotNull Duration initialInterval, @DecimalMin("1.0") double multiplier,
                        @NotNull Duration maxInterval, @PositiveOrZero int maxRetries) {
    }
}
