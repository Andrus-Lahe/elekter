package elekter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Holds the price limit and drives the relay: ON while the current price is at or below the limit. */
@Service
public class SwitchService {
    private static final Path LIMIT_FILE = Path.of("limit.txt");

    public record Status(Double currentPrice, double limit, boolean switchOn, String intervalStart, NtpClock.Info ntp) {}

    private final PriceService prices;
    private final Relay relay;
    private final NtpClock clock;
    private volatile double limit = loadLimit();

    public SwitchService(PriceService prices, Relay relay, NtpClock clock) {
        this.prices = prices;
        this.relay = relay;
        this.clock = clock;
    }

    public Status status() {
        NtpClock.Info ntp = clock.info();
        // A wrong clock makes every price lookup meaningless -> switch OFF.
        if (clock.isWrong()) return new Status(null, limit, false, null, ntp);

        Instant now = Instant.now();
        PriceService.Entry current = null;
        try {
            current = prices.entries().stream()
                    .filter(e -> !now.isBefore(e.start()) && now.isBefore(e.end()))
                    .findFirst().orElse(null);
        } catch (Exception ignored) {
            // no price info -> switch stays off
        }
        if (current == null) return new Status(null, limit, false, null, ntp);
        return new Status(current.price(), limit, current.price() <= limit, PriceService.formatEet(current.start()), ntp);
    }

    public void setLimit(double newLimit) throws IOException {
        limit = newLimit;
        Files.writeString(LIMIT_FILE, Double.toString(newLimit));
        apply();
    }

    /** Runs at startup and then every 3 minutes. */
    @Scheduled(fixedRate = 3 * 60 * 1000)
    public void apply() {
        relay.set(status().switchOn());
    }

    private static double loadLimit() {
        try {
            return Double.parseDouble(Files.readString(LIMIT_FILE).trim());
        } catch (IOException | NumberFormatException e) {
            return 100;
        }
    }
}
