package elekter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Day-ahead EE prices (EUR/MWh, 15-minute intervals) stored as one text file per Nord Pool delivery date,
 * e.g. {@code prices/2026-10-03.txt}, with lines {@code start;end;price} in EET.
 * Nord Pool delivery days follow CET, so a file runs from 01:00 EET of its date to 01:00 EET of the next day.
 */
@Service
public class PriceService {
    public record Entry(Instant start, Instant end, double price) {}

    public record LastDownload(String date, boolean ok, String at) {}

    /** Whether the files of today's and tomorrow's delivery date exist, and how the last download went. */
    public record FileStatus(boolean today, boolean tomorrow, LastDownload lastDownload) {}

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);
    private static final ZoneId EET = ZoneId.of("Europe/Tallinn");
    private static final ZoneId NORD_POOL = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Pattern ENTRY = Pattern.compile(
            "\"deliveryStart\":\"(.*?)\".*?\"deliveryEnd\":\"(.*?)\".*?\"EE\":(-?[\\d.]+)");

    private final Path dir;
    private volatile LastDownload lastDownload;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PriceService(@Value("${elekter.prices.dir:prices}") String dir) {
        this.dir = Path.of(dir);
    }

    /** The Nord Pool delivery date that contains the given moment. */
    public static LocalDate deliveryDate(Instant moment) {
        return moment.atZone(NORD_POOL).toLocalDate();
    }

    /** ISO timestamp in EET, e.g. {@code 2026-10-03T12:45:00+03:00}. */
    public static String formatEet(Instant moment) {
        return FORMAT.format(moment.atZone(EET));
    }

    public boolean hasFile(LocalDate date) {
        return Files.exists(file(date));
    }

    /** All saved prices, ordered by start time. */
    public List<Entry> entries() throws IOException {
        List<Entry> all = new ArrayList<>();
        for (Path file : files()) all.addAll(read(file));
        all.sort(Comparator.comparing(Entry::start));
        return all;
    }

    public FileStatus fileStatus(Instant now) {
        LocalDate today = deliveryDate(now);
        return new FileStatus(hasFile(today), hasFile(today.plusDays(1)), lastDownload);
    }

    /** Downloads the prices of one delivery date into its file. Returns false if nothing could be saved. */
    public boolean download(LocalDate date) {
        boolean ok = tryDownload(date);
        lastDownload = new LastDownload(date.toString(), ok, formatEet(Instant.now()));
        return ok;
    }

    private boolean tryDownload(LocalDate date) {
        try {
            List<Entry> entries = fetch(date);
            if (entries.isEmpty()) {
                log.warn("No prices available yet for {}", date);
                return false;
            }
            store(date, entries);
            log.info("Saved {} prices for {}", entries.size(), date);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("Price download for {} failed: {}", date, e.toString());
            return false;
        }
    }

    /** Deletes every file whose last interval has already ended. */
    public void deleteExpired(Instant now) throws IOException {
        for (Path file : files()) {
            List<Entry> entries = read(file);
            if (!entries.isEmpty() && entries.stream().allMatch(e -> !e.end().isAfter(now))) {
                Files.delete(file);
                log.info("Deleted expired price file {}", file.getFileName());
            }
        }
    }

    void store(LocalDate date, List<Entry> entries) throws IOException {
        Files.createDirectories(dir);
        Path tmp = dir.resolve(date + ".tmp");
        Files.write(tmp, entries.stream()
                .map(e -> formatEet(e.start()) + ";" + formatEet(e.end()) + ";" + e.price())
                .toList());
        Files.move(tmp, file(date), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private List<Entry> fetch(LocalDate date) throws IOException, InterruptedException {
        String url = "https://dataportal-api.nordpoolgroup.com/api/DayAheadPrices"
                + "?date=" + date + "&market=DayAhead&deliveryArea=EE&currency=EUR";
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return List.of();

        return ENTRY.matcher(response.body()).results()
                .map(m -> new Entry(Instant.parse(m.group(1)), Instant.parse(m.group(2)), Double.parseDouble(m.group(3))))
                .toList();
    }

    private List<Entry> read(Path file) {
        try {
            return Files.readAllLines(file).stream().filter(l -> !l.isBlank()).map(line -> {
                String[] p = line.split(";");
                return new Entry(OffsetDateTime.parse(p[0]).toInstant(), OffsetDateTime.parse(p[1]).toInstant(),
                        Double.parseDouble(p[2]));
            }).toList();
        } catch (Exception e) {
            log.warn("Ignoring unreadable price file {}: {}", file.getFileName(), e.toString());
            return List.of();
        }
    }

    private List<Path> files() throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(p -> p.toString().endsWith(".txt")).toList();
        }
    }

    private Path file(LocalDate date) {
        return dir.resolve(date + ".txt");
    }
}
