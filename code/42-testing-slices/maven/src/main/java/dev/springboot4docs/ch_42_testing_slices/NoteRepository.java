package dev.springboot4docs.ch_42_testing_slices;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, Long> {

	List<Note> findByTitleContainingIgnoreCase(String title);

}
