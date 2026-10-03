package elekter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelayTest {
    @TempDir
    Path dir;
    Relay relay;

    @AfterEach
    void tearDown() {
        if (relay != null) relay.shutdown();
    }

    /** A fake gpioset that logs its arguments and then runs for {@code lifetimeSeconds} (or until killed). */
    private String fakeGpioset(String lifetimeSeconds) throws IOException {
        Path script = dir.resolve("gpioset-" + lifetimeSeconds);
        Files.writeString(script, """
                #!/bin/bash
                if [ "$1" = "--version" ]; then echo "gpioset (libgpiod) v1.6.3"; exit 0; fi
                echo "$*" >> %s/calls.log
                trap 'exit 0' TERM INT
                sleep %s & wait $!
                """.formatted(dir, lifetimeSeconds));
        assertTrue(script.toFile().setExecutable(true));
        return script.toString();
    }

    private List<String> calls() throws IOException {
        Path log = dir.resolve("calls.log");
        return Files.exists(log) ? Files.readAllLines(log) : List.of();
    }

    private void awaitCalls(int expected) throws Exception {
        for (int i = 0; i < 100 && calls().size() < expected; i++) Thread.sleep(50);
    }

    @Test
    void disabledRelayOnlyRemembersTheState() {
        relay = new Relay(false, false, "gpiochip0", "gpioset");
        relay.set(true);

        Relay.Info info = relay.info();
        assertFalse(info.enabled());
        assertEquals(Boolean.TRUE, info.on());
        assertFalse(info.running());
    }

    @Test
    void holdsTheLineWhileRunningAndSwitchesLevel() throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        relay = new Relay(true, false, "gpiochip0", fakeGpioset("30"));

        assertNull(relay.info().on());
        relay.set(true);
        Relay.Info info = relay.info();
        assertTrue(info.enabled() && info.running());
        assertEquals(Boolean.TRUE, info.on());
        assertTrue(info.changedAt() != null);
        awaitCalls(1); // let the first process log before it is replaced

        relay.set(false);
        assertEquals(Boolean.FALSE, relay.info().on());
        assertTrue(relay.info().running());

        awaitCalls(2);
        assertEquals(List.of("--mode=signal gpiochip0 17=1", "--mode=signal gpiochip0 17=0"), calls());
    }

    @Test
    void restartsTheProcessWhenItHasDied() throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        relay = new Relay(true, false, "gpiochip0", fakeGpioset("0.3"));

        relay.set(true);
        assertTrue(relay.info().running());
        Thread.sleep(800);
        assertFalse(relay.info().running(), "the fake gpioset has exited");

        relay.set(true); // same state, but the process is gone -> start it again
        assertTrue(relay.info().running());
        awaitCalls(2);
        assertEquals(2, calls().size());
    }

    @Test
    void activeLowInvertsTheLevel() throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/bash")));
        relay = new Relay(true, true, "gpiochip0", fakeGpioset("30"));

        relay.set(true);
        awaitCalls(1);
        assertEquals(List.of("--mode=signal gpiochip0 17=0"), calls());
    }
}
