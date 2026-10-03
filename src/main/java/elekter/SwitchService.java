package elekter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Holds the price limit and drives the relay: ON while the current price is at or below the limit. */
@Service
public class SwitchService {
    private static final ZoneId EET = ZoneId.of("Europe/Tallinn");
    private static final Path LIMIT_FILE = Path.of("limit.txt");

    public record Status(Double currentPrice, double limit, boolean switchOn, String intervalStart) {}

    private final PriceService prices;
    private final Relay relay;
    private volatile double limit = loadLimit();

    public SwitchService(PriceService prices, Relay relay) {
        this.prices = prices;
        this.relay = relay;
    }

    public Status status() {
        Instant now = Instant.now();
        PriceService.Entry current = null;
        try {
            current = prices.entries().stream()
                    .filter(e -> !now.isBefore(e.start()) && now.isBefore(e.end()))
                    .findFirst().orElse(null);
        } catch (Exception ignored) {
            // no price info -> switch stays off
        }
        if (current == null) return new Status(null, limit, false, null);
        return new Status(current.price(), limit, current.price() <= limit,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(current.start().atZone(EET)));
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
