package elekter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class StatusController {
    private static final ZoneId EET = ZoneId.of("Europe/Tallinn");
    private static final Path LIMIT_FILE = Path.of("limit.txt");

    public record Status(Double currentPrice, double limit, boolean switchOn, String intervalStart) {}

    public record LimitUpdate(double limit) {}

    private final PriceService prices;
    private volatile double limit = loadLimit();

    public StatusController(PriceService prices) {
        this.prices = prices;
    }

    @GetMapping("/api/status")
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

    @PutMapping("/api/limit")
    public void setLimit(@RequestBody LimitUpdate update) throws IOException {
        if (update.limit() < -10 || update.limit() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between -10 and 1000");
        }
        limit = update.limit();
        Files.writeString(LIMIT_FILE, Double.toString(limit));
    }

    private static double loadLimit() {
        try {
            return Double.parseDouble(Files.readString(LIMIT_FILE).trim());
        } catch (IOException | NumberFormatException e) {
            return 100;
        }
    }
}
