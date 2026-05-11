package dev.springboot4docs.ch_48_tasks_api;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

	private final TaskService tasks;

	public TaskController(TaskService tasks) {
		this.tasks = tasks;
	}

	@GetMapping
	List<TaskResponse> list(JwtAuthenticationToken authentication) {
		return this.tasks.list(subject(authentication)).stream().map(TaskResponse::from).toList();
	}

	@PostMapping
	ResponseEntity<TaskResponse> create(@RequestBody CreateTaskRequest request,
			JwtAuthenticationToken authentication) {
		TaskItem task = this.tasks.create(subject(authentication), request.title(), request.details());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(task.getId())
				.toUri();
		return ResponseEntity.created(location).body(TaskResponse.from(task));
	}

	@PatchMapping("/{id}/complete")
	TaskResponse complete(@PathVariable long id, JwtAuthenticationToken authentication) {
		return TaskResponse.from(this.tasks.complete(subject(authentication), id));
	}

	@ExceptionHandler(TaskNotFoundException.class)
	ProblemDetail taskNotFound() {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problem.setTitle("Task not found");
		problem.setDetail("No task exists for the current user with that id.");
		return problem;
	}

	private static String subject(JwtAuthenticationToken authentication) {
		Jwt jwt = authentication.getToken();
		return jwt.getSubject();
	}

	public record CreateTaskRequest(String title, String details) {
	}

	public record TaskResponse(Long id, String title, String details, TaskStatus status,
			Instant createdAt, Instant completedAt) {

		static TaskResponse from(TaskItem task) {
			return new TaskResponse(task.getId(), task.getTitle(), task.getDetails(), task.getStatus(),
					task.getCreatedAt(), task.getCompletedAt());
		}

	}

}
