package elekter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PriceController {
    private static final Pattern ENTRY = Pattern.compile(
            "\"deliveryStart\":\"(.*?)\".*?\"deliveryEnd\":\"(.*?)\".*?\"EE\":(-?[\\d.]+)");

    /** Current and future 15-minute EE day-ahead prices (EUR/MWh), one per line. */
    @GetMapping(value = "/prices", produces = "text/plain")
    public String prices() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant now = Instant.now();
        List<String> lines = new ArrayList<>();

        for (LocalDate date : new LocalDate[]{today, today.plusDays(1)}) {
            String url = "https://dataportal-api.nordpoolgroup.com/api/DayAheadPrices"
                    + "?date=" + date + "&market=DayAhead&deliveryArea=EE&currency=EUR";
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) continue;

            ENTRY.matcher(response.body()).results()
                    .filter(m -> Instant.parse(m.group(2)).isAfter(now))
                    .forEach(m -> lines.add(m.group(1) + " - " + m.group(2) + " " + m.group(3)));
        }
        return String.join("\n", lines);
    }
}
