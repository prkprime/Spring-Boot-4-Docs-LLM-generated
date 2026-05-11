package dev.springboot4docs.ch_23_auditing;

import org.hibernate.annotations.SoftDelete;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
@SoftDelete
class Note extends Auditable {

	@Id
	@GeneratedValue
	private Long id;

	private String body;

	protected Note() {
	}

	Note(String body) {
		this.body = body;
	}

	public Long getId() {
		return this.id;
	}

	public String getBody() {
		return this.body;
	}

	public void updateBody(String body) {
		this.body = body;
	}

}
