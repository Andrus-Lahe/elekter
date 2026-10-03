package elekter;

import java.io.IOException;
import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PriceController {
    private final PriceService prices;

    public PriceController(PriceService prices) {
        this.prices = prices;
    }

    /** Current and future 15-minute EE day-ahead prices (EUR/MWh) from the saved files, EET, one per line. */
    @GetMapping(value = "/prices", produces = "text/plain")
    public String prices() throws IOException {
        Instant now = Instant.now();
        return prices.entries().stream()
                .filter(e -> e.end().isAfter(now))
                .map(e -> PriceService.formatEet(e.start()) + " - " + PriceService.formatEet(e.end()) + " " + e.price())
                .collect(Collectors.joining("\n"));
    }
}
