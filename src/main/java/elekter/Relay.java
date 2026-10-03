package elekter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Relay on GPIO17 (BCM numbering), driven through the libgpiod {@code gpioset} command.
 * The line is held by a long-running {@code gpioset} process; when the app stops the line is released.
 */
@Component
public class Relay {
    /** {@code running}: the gpioset process holding the line is alive (only meaningful when {@code enabled}). */
    public record Info(boolean enabled, Boolean on, boolean running, String changedAt) {}

    private static final Logger log = LoggerFactory.getLogger(Relay.class);
    private static final int LINE = 17;

    private final boolean enabled;
    private final boolean activeLow;
    private final String chip;
    private final String command;

    private Process process;
    private Boolean state;
    private Instant changedAt;
    private Integer libgpiodMajor;

    public Relay(@Value("${elekter.gpio.enabled:false}") boolean enabled,
                 @Value("${elekter.gpio.active-low:false}") boolean activeLow,
                 @Value("${elekter.gpio.chip:gpiochip0}") String chip,
                 @Value("${elekter.gpio.command:gpioset}") String command) {
        this.enabled = enabled;
        this.activeLow = activeLow;
        this.chip = chip;
        this.command = command;
    }

    public synchronized Info info() {
        return new Info(enabled, state, process != null && process.isAlive(),
                changedAt == null ? null : PriceService.formatEet(changedAt));
    }

    public synchronized void set(boolean on) {
        boolean changed = !Boolean.valueOf(on).equals(state);
        if (changed) {
            log.info("Relay GPIO{} -> {}", LINE, on ? "ON" : "OFF");
            changedAt = Instant.now();
        }
        if (!enabled) {
            state = on;
            return;
        }
        if (!changed && process != null && process.isAlive()) return;
        if (!changed) log.warn("{} process had exited, restarting it", command);

        try {
            stop();
            process = new ProcessBuilder(command(on ^ activeLow ? 1 : 0))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            state = on;
        } catch (IOException | InterruptedException e) {
            state = null;
            log.error("Could not drive GPIO{}: {}", LINE, e.toString());
        }
    }

    @PreDestroy
    synchronized void shutdown() {
        stop();
        state = null;
    }

    private void stop() {
        if (process != null) {
            process.destroy();
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            process = null;
        }
    }

    private List<String> command(int level) throws IOException, InterruptedException {
        // libgpiod v1: "gpioset --mode=signal CHIP LINE=VALUE"; v2 (holds the line until killed): "gpioset -c CHIP LINE=VALUE"
        return major() >= 2
                ? List.of(command, "-c", chip, LINE + "=" + level)
                : List.of(command, "--mode=signal", chip, LINE + "=" + level);
    }

    private int major() throws IOException, InterruptedException {
        if (libgpiodMajor == null) {
            Process p = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            Matcher m = Pattern.compile("v(\\d+)").matcher(out);
            libgpiodMajor = m.find() ? Integer.parseInt(m.group(1)) : 1;
        }
        return libgpiodMajor;
    }
}
