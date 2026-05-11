package dev.springboot4docs.ch_02_dev_loop;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TimeController {

	@GetMapping("/now")
	TimeResponse now() {
		return new TimeResponse(Instant.now());
	}

	record TimeResponse(Instant now) {
	}

}
