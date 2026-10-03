package elekter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PriceServiceTest {
    // Nord Pool delivery day 2026-10-03 (CEST) runs from 2026-10-02T22:00Z to 2026-10-03T22:00Z.
    private static final Instant DAY1 = Instant.parse("2026-10-02T22:00:00Z");
    private static final Instant DAY2 = DAY1.plus(Duration.ofDays(1));

    @TempDir
    Path dir;
    PriceService service;

    @BeforeEach
    void setUp() {
        service = new PriceService(dir.toString());
    }

    private static List<PriceService.Entry> day(Instant start, double firstPrice) {
        List<PriceService.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 96; i++) {
            Instant s = start.plus(Duration.ofMinutes(15L * i));
            entries.add(new PriceService.Entry(s, s.plus(Duration.ofMinutes(15)), firstPrice + i));
        }
        return entries;
    }

    @Test
    void savesOneFilePerDateInEetAndReadsThemBackInOrder() throws IOException {
        service.store(LocalDate.parse("2026-10-04"), day(DAY2, 200));
        service.store(LocalDate.parse("2026-10-03"), day(DAY1, 100));

        assertTrue(service.hasFile(LocalDate.parse("2026-10-03")));
        assertTrue(service.hasFile(LocalDate.parse("2026-10-04")));
        assertFalse(service.hasFile(LocalDate.parse("2026-10-05")));

        List<PriceService.Entry> entries = service.entries();
        assertEquals(192, entries.size());
        assertEquals(DAY1, entries.get(0).start());
        assertEquals(100.0, entries.get(0).price());
        assertEquals(DAY2.plus(Duration.ofMinutes(15 * 95)), entries.get(191).start());

        String firstLine = Files.readAllLines(dir.resolve("2026-10-03.txt")).get(0);
        assertEquals("2026-10-03T01:00:00+03:00;2026-10-03T01:15:00+03:00;100.0", firstLine);
    }

    @Test
    void deletesAFileOnlyAfterItsLastIntervalHasEnded() throws IOException {
        service.store(LocalDate.parse("2026-10-03"), day(DAY1, 100));
        service.store(LocalDate.parse("2026-10-04"), day(DAY2, 200));

        // 2026-10-03T21:59Z: the last interval of the first file (21:45-22:00Z) is still running.
        service.deleteExpired(Instant.parse("2026-10-03T21:59:00Z"));
        assertTrue(service.hasFile(LocalDate.parse("2026-10-03")));

        service.deleteExpired(Instant.parse("2026-10-03T22:05:00Z"));
        assertFalse(service.hasFile(LocalDate.parse("2026-10-03")));
        assertTrue(service.hasFile(LocalDate.parse("2026-10-04")));
    }

    @Test
    void deliveryDateFollowsCet() {
        // 00:30 EEST on 4 Oct is 23:30 CEST on 3 Oct, which belongs to delivery date 3 Oct.
        assertEquals(LocalDate.parse("2026-10-03"), PriceService.deliveryDate(Instant.parse("2026-10-03T21:30:00Z")));
        assertEquals(LocalDate.parse("2026-10-04"), PriceService.deliveryDate(Instant.parse("2026-10-03T22:00:00Z")));
    }

    @Test
    void missingDirectoryMeansNoPrices() throws IOException {
        assertTrue(new PriceService(dir.resolve("nope").toString()).entries().isEmpty());
    }
}
