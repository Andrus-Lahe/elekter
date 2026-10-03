package elekter;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class StatusController {
    public record LimitUpdate(double limit) {}

    private final SwitchService switchService;

    public StatusController(SwitchService switchService) {
        this.switchService = switchService;
    }

    @GetMapping("/api/status")
    public SwitchService.Status status() {
        return switchService.status();
    }

    @PutMapping("/api/limit")
    public void setLimit(@RequestBody LimitUpdate update) throws IOException {
        if (update.limit() < -10 || update.limit() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between -10 and 1000");
        }
        switchService.setLimit(update.limit());
    }
}
