package dev.springboot4docs.ch_42_testing_slices;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class NoteJsonTest {

	@Autowired
	private JacksonTester<NoteController.NoteRequest> requestJson;

	@Autowired
	private JacksonTester<NoteController.NoteResponse> responseJson;

	@Test
	void serializesAResponse() throws Exception {
		NoteController.NoteResponse response = new NoteController.NoteResponse(12L, "JSON slice", "Only Jackson is loaded.");

		assertThat(this.responseJson.write(response)).isStrictlyEqualToJson("""
				{"id":12,"title":"JSON slice","body":"Only Jackson is loaded."}
				""");
	}

	@Test
	void deserializesARequest() throws Exception {
		NoteController.NoteRequest request = this.requestJson.parseObject("""
				{"title":"Incoming","body":"Read from JSON"}
				""");

		assertThat(request.title()).isEqualTo("Incoming");
		assertThat(request.body()).isEqualTo("Read from JSON");
	}

	@Test
	void roundTripsAResponse() throws Exception {
		NoteController.NoteResponse response = new NoteController.NoteResponse(8L, "Round trip", "Serialize then parse.");

		NoteController.NoteResponse parsed = this.responseJson.parseObject(this.responseJson.write(response).getJson());

		assertThat(parsed).isEqualTo(response);
	}

}
