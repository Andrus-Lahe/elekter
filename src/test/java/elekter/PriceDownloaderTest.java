package elekter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class PriceDownloaderTest {
    /** download() succeeds on the given call number (1-based); -1 means never. */
    static class FakePrices extends PriceService {
        final AtomicInteger calls = new AtomicInteger();
        final int succeedOn;

        FakePrices(Path dir, int succeedOn) {
            super(dir.toString());
            this.succeedOn = succeedOn;
        }

        @Override
        public boolean download(LocalDate date) {
            return calls.incrementAndGet() == succeedOn;
        }
    }

    @TempDir
    Path dir;
    ThreadPoolTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    private PriceDownloader downloader(FakePrices prices) {
        NtpClock clock = new NtpClock(scheduler, "localhost", 123, 60);
        return new PriceDownloader(prices, new SwitchService(prices, new Relay(false, false, "gpiochip0"), clock),
                clock, scheduler, 0);
    }

    private static void awaitCalls(FakePrices prices, int expected) throws InterruptedException {
        for (int i = 0; i < 250 && prices.calls.get() < expected; i++) Thread.sleep(20);
    }

    @Test
    void stopsRetryingAfterSuccess() throws InterruptedException {
        FakePrices prices = new FakePrices(dir, 3);
        downloader(prices).downloadWithRetry(LocalDate.parse("2026-10-04"), 3);

        awaitCalls(prices, 3);
        Thread.sleep(300);
        assertEquals(3, prices.calls.get());
    }

    @Test
    void triesOnceAndThenThreeTimesBeforeGivingUp() throws InterruptedException {
        FakePrices prices = new FakePrices(dir, -1);
        downloader(prices).downloadWithRetry(LocalDate.parse("2026-10-04"), 3);

        awaitCalls(prices, 4);
        Thread.sleep(300);
        assertEquals(4, prices.calls.get());
    }
}
