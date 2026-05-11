package dev.springboot4docs.ch_03_configuration_basics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

	private final GreetingProperties greeting;

	public GreetingController(GreetingProperties greeting) {
		this.greeting = greeting;
	}

	@GetMapping("/greet")
	public String greet() {
		return "%s, %s%s".formatted(greeting.prefix(), greeting.audience(), greeting.punctuation());
	}

}
