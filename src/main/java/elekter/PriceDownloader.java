package elekter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps the price files up to date: tomorrow's prices at 14:30 EET (3 retries, 2 minutes apart) and cleanup. */
@Component
public class PriceDownloader {
    private static final Logger log = LoggerFactory.getLogger(PriceDownloader.class);
    private static final ZoneId EET = ZoneId.of("Europe/Tallinn");
    private static final LocalTime DAILY_TIME = LocalTime.of(14, 30);
    private static final int RETRIES = 3;

    private final PriceService prices;
    private final SwitchService switchService;
    private final TaskScheduler scheduler;
    private final long retrySeconds;

    public PriceDownloader(PriceService prices, SwitchService switchService, TaskScheduler scheduler,
                           @Value("${elekter.download.retry-seconds:120}") long retrySeconds) {
        this.prices = prices;
        this.switchService = switchService;
        this.scheduler = scheduler;
        this.retrySeconds = retrySeconds;
    }

    /** Every day at 14:30 EET: download tomorrow's prices. */
    @Scheduled(cron = "0 30 14 * * *", zone = "Europe/Tallinn")
    public void downloadTomorrow() {
        downloadWithRetry(PriceService.deliveryDate(Instant.now()).plusDays(1), RETRIES);
    }

    /**
     * Runs at startup and then every 15 minutes: fetches whatever is missing, e.g. after the Pi was off or had no network
     * at 14:30 — today's file if no price covers the present moment, tomorrow's if it is past 14:30 and the file is missing.
     */
    @Scheduled(fixedDelay = 15 * 60 * 1000)
    public void catchUp() throws IOException {
        Instant now = Instant.now();
        boolean downloaded = false;

        if (prices.entries().stream().noneMatch(e -> !now.isBefore(e.start()) && now.isBefore(e.end()))) {
            downloaded |= prices.download(PriceService.deliveryDate(now));
        }
        LocalDate tomorrow = PriceService.deliveryDate(now).plusDays(1);
        if (!ZonedDateTime.ofInstant(now, EET).toLocalTime().isBefore(DAILY_TIME) && !prices.hasFile(tomorrow)) {
            downloaded |= prices.download(tomorrow);
        }
        if (downloaded) switchService.apply();
    }

    /** Hourly at :05 — an expired file goes right after its last interval ends (01:00 EET, see {@link PriceService}). */
    @Scheduled(cron = "0 5 * * * *")
    public void cleanup() throws IOException {
        prices.deleteExpired(Instant.now());
    }

    void downloadWithRetry(LocalDate date, int retriesLeft) {
        if (prices.download(date)) {
            switchService.apply();
        } else if (retriesLeft > 0) {
            log.warn("Retrying download for {} in {} s ({} retries left)", date, retrySeconds, retriesLeft);
            scheduler.schedule(() -> downloadWithRetry(date, retriesLeft - 1), Instant.now().plusSeconds(retrySeconds));
        } else {
            log.error("Giving up downloading prices for {}", date);
        }
    }
}
