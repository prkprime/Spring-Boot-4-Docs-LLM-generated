package dev.springboot4docs.ch_23_auditing;

import org.hibernate.annotations.SoftDelete;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@SoftDelete
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Note extends Auditable {

    @Id
    @GeneratedValue
    private Long id;

    private String body;

    Note(String body) {
        this.body = body;
    }

    public void updateBody(String body) {
        this.body = body;
    }

}
