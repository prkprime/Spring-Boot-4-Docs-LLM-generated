package dev.springboot4docs.ch_42_testing_slices;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteService {

    private final NoteRepository notes;

    public NoteService(NoteRepository notes) {
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public List<Note> list() {
        return this.notes.findAll();
    }

    @Transactional(readOnly = true)
    public Note get(long id) {
        return this.notes.findById(id).orElseThrow(NoteNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<Note> search(String title) {
        return this.notes.findByTitleContainingIgnoreCase(title);
    }

    @Transactional
    public Note create(String title, String body) {
        return this.notes.save(new Note(title, body));
    }

    @Transactional
    public Note update(long id, String title, String body) {
        Note note = get(id);
        note.rename(title, body);
        return note;
    }

    @Transactional
    public void delete(long id) {
        if (!this.notes.existsById(id)) {
            throw new NoteNotFoundException();
        }
        this.notes.deleteById(id);
    }

}
