package dev.springboot4docs.ch_44_virtual_threads;

import java.util.concurrent.Callable;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PinController {

    private final Object monitor = new Object();

    @GetMapping("/pin")
    Callable<PinReport> pin(@RequestParam(defaultValue = "25") long ms) {
        return () -> {
            synchronized (this.monitor) {
                Thread.sleep(ms);
                Thread thread = Thread.currentThread();
                return new PinReport(thread.toString(), thread.isVirtual(),
                        "Sleeping while holding a monitor pins the virtual thread until the block exits.");
            }
        };
    }

    record PinReport(String thread, boolean virtual, String note) {
    }

}
