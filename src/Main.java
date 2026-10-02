import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

void main() throws Exception {
    String[] areas = {"EE"};
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    Instant now = Instant.now();

    List<PriceEntry> entries = new ArrayList<>();
    entries.addAll(fetchEntries(today, areas));
    entries.addAll(fetchEntries(today.plusDays(1), areas));

    entries.stream()
            .filter(entry -> entry.deliveryEnd().isAfter(now))
            .forEach(System.out::println);
}

List<PriceEntry> fetchEntries(LocalDate date, String[] areas) throws Exception {
    String url = "https://dataportal-api.nordpoolgroup.com/api/DayAheadPrices"
            + "?date=" + date
            + "&market=DayAhead"
            + "&deliveryArea=" + String.join(",", areas)
            + "&currency=EUR";

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
        return List.of();
    }
    return parseEntries(response.body());
}

List<PriceEntry> parseEntries(String json) {
    List<PriceEntry> entries = new ArrayList<>();
    Matcher matcher = Pattern.compile(
            "\"deliveryStart\":\"(.*?)\".*?\"deliveryEnd\":\"(.*?)\".*?\"entryPerArea\":\\{(.*?)\\}"
    ).matcher(json);

    while (matcher.find()) {
        Instant start = Instant.parse(matcher.group(1));
        Instant end = Instant.parse(matcher.group(2));
        Map<String, Double> prices = parsePrices(matcher.group(3));
        entries.add(new PriceEntry(start, end, prices));
    }
    return entries;
}

Map<String, Double> parsePrices(String entryPerArea) {
    Map<String, Double> prices = new LinkedHashMap<>();
    Matcher matcher = Pattern.compile("\"(\\w+)\":(-?[\\d.]+)").matcher(entryPerArea);
    while (matcher.find()) {
        prices.put(matcher.group(1), Double.parseDouble(matcher.group(2)));
    }
    return prices;
}

record PriceEntry(Instant deliveryStart, Instant deliveryEnd, Map<String, Double> prices) {
    @Override
    public String toString() {
        return deliveryStart + " - " + deliveryEnd + " " + prices;
    }
}
