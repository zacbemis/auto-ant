package com.gei.autoant.watch;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebouncerTest {
    @Test
    void groupsPendingChangesIntoOneBatch() {
        List<ChangeBatch> batches = new ArrayList<>();

        try (Debouncer debouncer = new Debouncer(Duration.ofSeconds(30), batches::add)) {
            debouncer.submit(Path.of("web/index.jsp"));
            debouncer.submit(Path.of("web/assets/app.css"));
            debouncer.submit(Path.of("web/index.jsp"));

            assertTrue(debouncer.hasPending());
            debouncer.flushNow();
            assertFalse(debouncer.hasPending());
        }

        assertEquals(1, batches.size());
        assertEquals(List.of(Path.of("web/index.jsp"), Path.of("web/assets/app.css")), batches.get(0).paths());
    }
}