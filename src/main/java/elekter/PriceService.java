package elekter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class PriceService {
    public record Entry(Instant start, Instant end, double price) {}

    private static final Pattern ENTRY = Pattern.compile(
            "\"deliveryStart\":\"(.*?)\".*?\"deliveryEnd\":\"(.*?)\".*?\"EE\":(-?[\\d.]+)");

    private List<Entry> cache = List.of();
    private Instant fetchedAt;

    /** Today's and tomorrow's 15-minute EE prices (EUR/MWh), cached for 5 minutes. */
    public synchronized List<Entry> entries() throws Exception {
        if (fetchedAt == null || Duration.between(fetchedAt, Instant.now()).toMinutes() >= 5) {
            cache = fetch();
            fetchedAt = Instant.now();
        }
        return cache;
    }

    private List<Entry> fetch() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Entry> entries = new ArrayList<>();

        for (LocalDate date : new LocalDate[]{today, today.plusDays(1)}) {
            String url = "https://dataportal-api.nordpoolgroup.com/api/DayAheadPrices"
                    + "?date=" + date + "&market=DayAhead&deliveryArea=EE&currency=EUR";
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) continue;

            ENTRY.matcher(response.body()).results().forEach(m -> entries.add(new Entry(
                    Instant.parse(m.group(1)), Instant.parse(m.group(2)), Double.parseDouble(m.group(3)))));
        }
        return entries;
    }
}
