package dev.springboot4docs.ch_41_graceful_shutdown;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SlowController {

	private static final Logger log = LoggerFactory.getLogger(SlowController.class);

	@GetMapping("/work")
	String work(@RequestParam(defaultValue = "3") int seconds) throws InterruptedException {
		Duration duration = Duration.ofSeconds(Math.min(Math.max(seconds, 1), 30));
		log.info("starting {} seconds of work", duration.toSeconds());
		Thread.sleep(duration);
		log.info("finished {} seconds of work", duration.toSeconds());
		return "OK";
	}

}
