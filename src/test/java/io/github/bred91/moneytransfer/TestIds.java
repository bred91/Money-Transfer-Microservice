package io.github.bred91.moneytransfer;

import java.util.UUID;

/**
 * Deterministic UUIDs for tests: {@code id(1)} -> {@code 00000000-0000-0000-0000-000000000001}.
 */
public final class TestIds {

    private TestIds() {
    }

    public static UUID id(long n) {
        return new UUID(0, n);
    }
}
