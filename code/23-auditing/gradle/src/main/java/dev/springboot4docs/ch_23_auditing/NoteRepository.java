package dev.springboot4docs.ch_23_auditing;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NoteRepository extends JpaRepository<Note, Long> {

    @Query(value = "select deleted from note where id = :id", nativeQuery = true)
    Optional<Boolean> findDeletedFlagByIdIncludingDeleted(@Param("id") Long id);

}
