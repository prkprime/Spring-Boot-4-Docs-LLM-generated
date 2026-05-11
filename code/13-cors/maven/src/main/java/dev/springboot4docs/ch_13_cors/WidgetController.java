package dev.springboot4docs.ch_13_cors;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WidgetController {

	private final List<Widget> widgets = List.of(
			new Widget(1, "Roadrunner"),
			new Widget(2, "Anvil"));

	@GetMapping("/widgets/all")
	ResponseEntity<List<Widget>> all() {
		return ResponseEntity.ok()
				.header("X-Total-Count", Integer.toString(this.widgets.size()))
				.body(this.widgets);
	}

	@CrossOrigin(origins = "https://localhost:3000")
	@GetMapping("/widgets/local")
	List<Widget> local() {
		return this.widgets;
	}

	record Widget(int id, String name) {
	}

}
