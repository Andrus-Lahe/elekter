package elekter;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PriceController {
    private final PriceService prices;

    public PriceController(PriceService prices) {
        this.prices = prices;
    }

    /** Current and future 15-minute EE day-ahead prices (EUR/MWh), one per line. */
    @GetMapping(value = "/prices", produces = "text/plain")
    public String prices() throws Exception {
        Instant now = Instant.now();
        return prices.entries().stream()
                .filter(e -> e.end().isAfter(now))
                .map(e -> e.start() + " - " + e.end() + " " + e.price())
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
