package elekter;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Checks the system clock against an NTP server (SNTP) every hour — every 2 minutes while the clock is wrong or the
 * server cannot be reached. It only measures: the clock itself is corrected by the operating system
 * (systemd-timesyncd). While the clock is off by more than the threshold, the switch stays OFF.
 */
@Component
public class NtpClock {
    public record Info(String state, Long offsetMillis, String checkedAt) {}

    private static final Logger log = LoggerFactory.getLogger(NtpClock.class);
    private static final long NTP_EPOCH_OFFSET_SECONDS = 2_208_988_800L;
    private static final Duration NORMAL_INTERVAL = Duration.ofHours(1);
    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(2);

    private final TaskScheduler scheduler;
    private final String server;
    private final int port;
    private final long thresholdMillis;

    private volatile Long offsetMillis;
    private volatile Instant checkedAt;
    private volatile boolean lastAttemptFailed;

    public NtpClock(TaskScheduler scheduler,
                    @Value("${elekter.ntp.server:pool.ntp.org}") String server,
                    @Value("${elekter.ntp.port:123}") int port,
                    @Value("${elekter.ntp.threshold-seconds:60}") long thresholdSeconds) {
        this.scheduler = scheduler;
        this.server = server;
        this.port = port;
        this.thresholdMillis = thresholdSeconds * 1000;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        scheduler.schedule(this::checkAndReschedule, Instant.now());
    }

    private void checkAndReschedule() {
        boolean good = check() && !isWrong();
        scheduler.schedule(this::checkAndReschedule, Instant.now().plus(good ? NORMAL_INTERVAL : RETRY_INTERVAL));
    }

    /** True while the last successful check found the clock off by more than the threshold. */
    public boolean isWrong() {
        Long offset = offsetMillis;
        return offset != null && Math.abs(offset) > thresholdMillis;
    }

    public Info info() {
        Long offset = offsetMillis;
        String state = isWrong() ? "wrong" : lastAttemptFailed ? "unreachable" : offset == null ? "unknown" : "ok";
        return new Info(state, offset, checkedAt == null ? null : PriceService.formatEet(checkedAt));
    }

    /** Asks the server once. Returns false if no usable answer came back. */
    boolean check() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);
            socket.connect(InetAddress.getByName(server), port);

            byte[] request = new byte[48];
            request[0] = 0x23; // leap indicator 0, version 4, mode 3 (client)
            long t1 = System.currentTimeMillis();
            socket.send(new DatagramPacket(request, request.length));

            DatagramPacket response = new DatagramPacket(new byte[48], 48);
            socket.receive(response);
            long t4 = System.currentTimeMillis();

            byte[] b = response.getData();
            boolean unusable = response.getLength() < 48 || (b[0] & 7) != 4 || (b[0] >> 6 & 3) == 3 || b[1] == 0;
            if (unusable) throw new IllegalStateException("unusable NTP response from " + server);

            long t2 = timestampMillis(b, 32);
            long t3 = timestampMillis(b, 40);
            offsetMillis = ((t2 - t1) + (t3 - t4)) / 2;
            checkedAt = Instant.now();
            lastAttemptFailed = false;
            if (isWrong()) log.warn("System clock is off by {} ms (NTP {})", offsetMillis, server);
            else log.info("NTP check ok: clock offset {} ms ({})", offsetMillis, server);
            return true;
        } catch (Exception e) {
            lastAttemptFailed = true;
            log.warn("NTP check against {} failed: {}", server, e.toString());
            return false;
        }
    }

    private static long timestampMillis(byte[] b, int offset) {
        long seconds = unsigned32(b, offset);
        long fraction = unsigned32(b, offset + 4);
        if ((seconds & 0x8000_0000L) == 0) seconds += 1L << 32; // NTP era 1 (after 2036)
        return (seconds - NTP_EPOCH_OFFSET_SECONDS) * 1000 + (fraction * 1000 >> 32);
    }

    private static long unsigned32(byte[] b, int offset) {
        return ((b[offset] & 0xFFL) << 24) | ((b[offset + 1] & 0xFFL) << 16)
                | ((b[offset + 2] & 0xFFL) << 8) | (b[offset + 3] & 0xFFL);
    }
}
