package dev.springboot4docs.ch_23_auditing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import jakarta.persistence.EntityManager;

@DataJpaTest
@Import(AuditConfig.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class NoteRepositoryTests {

	@Autowired
	private NoteRepository notes;

	@Autowired
	private EntityManager entityManager;

	@Test
	void insertPopulatesAuditColumns() {
		Note saved = this.notes.saveAndFlush(new Note("Draft release notes"));

		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(saved.getCreatedBy()).isEqualTo("system");
		assertThat(saved.getUpdatedBy()).isEqualTo("system");
	}

	@Test
	void updateChangesUpdatedAtButNotCreatedAt() throws InterruptedException {
		Note saved = this.notes.saveAndFlush(new Note("Draft release notes"));
		Instant createdAt = saved.getCreatedAt();
		Instant firstUpdatedAt = saved.getUpdatedAt();

		Thread.sleep(20);
		saved.updateBody("Final release notes");
		this.notes.saveAndFlush(saved);

		assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
		assertThat(saved.getUpdatedAt()).isAfter(firstUpdatedAt);
	}

	@Test
	void deleteMarksTheRowButRepositoryQueriesHideIt() {
		Note saved = this.notes.saveAndFlush(new Note("Keep history for audit"));
		Long id = saved.getId();

		this.notes.delete(saved);
		this.notes.flush();
		this.entityManager.clear();

		assertThat(this.notes.findAll()).isEmpty();
		assertThat(this.notes.findById(id)).isEmpty();
		assertThat(this.notes.findDeletedFlagByIdIncludingDeleted(id)).contains(true);
	}

}
