package dev.springboot4docs.ch_44_virtual_threads;

import java.util.concurrent.Callable;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlockingController {

	@GetMapping("/sleep")
	Callable<String> sleep(@RequestParam(defaultValue = "100") long ms) {
		return () -> {
			Thread.sleep(ms);
			return Thread.currentThread().toString();
		};
	}

}
