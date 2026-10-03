import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

void main() throws Exception {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    Instant now = Instant.now();

    for (LocalDate date : new LocalDate[]{today, today.plusDays(1)}) {
        String url = "https://dataportal-api.nordpoolgroup.com/api/DayAheadPrices"
                + "?date=" + date + "&market=DayAhead&deliveryArea=EE&currency=EUR";
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) continue;

        Pattern.compile("\"deliveryStart\":\"(.*?)\".*?\"deliveryEnd\":\"(.*?)\".*?\"EE\":(-?[\\d.]+)")
                .matcher(response.body()).results()
                .filter(m -> Instant.parse(m.group(2)).isAfter(now))
                .forEach(m -> System.out.println(m.group(1) + " - " + m.group(2) + " " + m.group(3)));
    }
}
