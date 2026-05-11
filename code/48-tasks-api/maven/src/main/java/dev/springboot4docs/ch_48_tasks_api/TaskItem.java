package dev.springboot4docs.ch_48_tasks_api;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "task_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String ownerSubject;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(columnDefinition = "text")
	private String details;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TaskStatus status = TaskStatus.OPEN;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant completedAt;

	public TaskItem(String ownerSubject, String title, String details) {
		this.ownerSubject = ownerSubject;
		this.title = title;
		this.details = details;
	}

	@PrePersist
	void prePersist() {
		if (this.createdAt == null) {
			this.createdAt = Instant.now();
		}
	}

	public void complete() {
		this.status = TaskStatus.DONE;
		this.completedAt = Instant.now();
	}

}
