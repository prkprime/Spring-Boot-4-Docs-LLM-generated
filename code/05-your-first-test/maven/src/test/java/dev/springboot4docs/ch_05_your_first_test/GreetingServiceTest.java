package dev.springboot4docs.ch_05_your_first_test;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GreetingServiceTest {

	private final GreetingService greetingService = new GreetingService();

	@Test
	void greetsByName() {
		Greeting greeting = this.greetingService.greet("Spring");

		assertThat(greeting.text()).isEqualTo("Hello, Spring!");
	}

}
