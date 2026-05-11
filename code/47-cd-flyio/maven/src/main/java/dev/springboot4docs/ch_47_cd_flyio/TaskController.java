package dev.springboot4docs.ch_47_cd_flyio;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TaskController {

	@GetMapping("/api/tasks")
	List<Task> tasks() {
		return List.of(
				new Task(1, "Build the image", true),
				new Task(2, "Deploy to Fly.io", false));
	}

	record Task(long id, String title, boolean complete) {
	}

}
