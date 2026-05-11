package dev.springboot4docs.ch_48_tasks_api;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

	private final TaskRepository tasks;

	public TaskService(TaskRepository tasks) {
		this.tasks = tasks;
	}

	@Transactional(readOnly = true)
	public List<TaskItem> list(String ownerSubject) {
		return this.tasks.findByOwnerSubjectOrderByCreatedAtDesc(ownerSubject);
	}

	@Transactional
	public TaskItem create(String ownerSubject, String title, String details) {
		return this.tasks.save(new TaskItem(ownerSubject, title, details));
	}

	@Transactional
	public TaskItem complete(String ownerSubject, long id) {
		TaskItem task = this.tasks.findByIdAndOwnerSubject(id, ownerSubject)
				.orElseThrow(TaskNotFoundException::new);
		task.complete();
		return task;
	}

}
