package dev.springboot4docs.ch_48_tasks_api;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<TaskItem, Long> {

    List<TaskItem> findByOwnerSubjectOrderByCreatedAtDesc(String ownerSubject);

    Optional<TaskItem> findByIdAndOwnerSubject(Long id, String ownerSubject);

    void deleteByOwnerSubject(String ownerSubject);

}
