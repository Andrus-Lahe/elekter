package elekter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class NtpClockTest {
    private static final long NTP_EPOCH_OFFSET_SECONDS = 2_208_988_800L;

    private DatagramSocket server;
    private Thread serverThread;

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
    }

    /** A fake NTP server on loopback whose clock runs {@code skewMillis} ahead of the local one. */
    private int startServer(long skewMillis, int stratum) throws Exception {
        server = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        serverThread = new Thread(() -> {
            try {
                byte[] buf = new byte[48];
                DatagramPacket request = new DatagramPacket(buf, buf.length);
                server.receive(request);
                byte[] reply = new byte[48];
                reply[0] = 0x24; // leap 0, version 4, mode 4 (server)
                reply[1] = (byte) stratum;
                long ntpMillis = System.currentTimeMillis() + skewMillis;
                writeTimestamp(reply, 32, ntpMillis);
                writeTimestamp(reply, 40, ntpMillis);
                server.send(new DatagramPacket(reply, reply.length, request.getSocketAddress()));
            } catch (Exception ignored) {
                // socket closed
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        return server.getLocalPort();
    }

    private static void writeTimestamp(byte[] b, int offset, long unixMillis) {
        long seconds = unixMillis / 1000 + NTP_EPOCH_OFFSET_SECONDS;
        long fraction = (unixMillis % 1000) * (1L << 32) / 1000;
        for (int i = 0; i < 4; i++) {
            b[offset + i] = (byte) (seconds >> (24 - 8 * i));
            b[offset + 4 + i] = (byte) (fraction >> (24 - 8 * i));
        }
    }

    private static NtpClock clock(int port) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        return new NtpClock(scheduler, "127.0.0.1", port, 60);
    }

    @Test
    void unknownBeforeTheFirstCheck() {
        NtpClock clock = clock(1);
        assertEquals("unknown", clock.info().state());
        assertFalse(clock.isWrong());
    }

    @Test
    void accurateClockIsOk() throws Exception {
        NtpClock clock = clock(startServer(0, 2));

        assertTrue(clock.check());
        assertEquals("ok", clock.info().state());
        assertTrue(Math.abs(clock.info().offsetMillis()) < 1000, "offset " + clock.info().offsetMillis());
        assertNotNull(clock.info().checkedAt());
        assertFalse(clock.isWrong());
    }

    @Test
    void clockFiveMinutesBehindIsWrong() throws Exception {
        // the server is 5 minutes ahead of us, so our clock is 5 minutes behind
        NtpClock clock = clock(startServer(5 * 60 * 1000, 2));

        assertTrue(clock.check());
        assertTrue(clock.isWrong());
        assertEquals("wrong", clock.info().state());
        assertTrue(Math.abs(clock.info().offsetMillis() - 300_000) < 1000, "offset " + clock.info().offsetMillis());
    }

    @Test
    void unusableAnswerCountsAsFailure() throws Exception {
        NtpClock clock = clock(startServer(0, 0)); // stratum 0 = kiss-o'-death

        assertFalse(clock.check());
        assertEquals("unreachable", clock.info().state());
    }

    @Test
    void unreachableServerKeepsTheSwitchAllowed() throws Exception {
        int port;
        try (DatagramSocket closed = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            port = closed.getLocalPort();
        }
        NtpClock clock = clock(port);

        assertFalse(clock.check());
        assertEquals("unreachable", clock.info().state());
        assertFalse(clock.isWrong());
    }
}
