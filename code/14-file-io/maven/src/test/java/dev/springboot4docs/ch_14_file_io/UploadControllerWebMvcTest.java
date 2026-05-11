package dev.springboot4docs.ch_14_file_io;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(UploadController.class)
@TestPropertySource(properties = {
		"app.upload-dir=target/test-uploads",
		"spring.servlet.multipart.max-file-size=1KB",
		"spring.servlet.multipart.max-request-size=2KB"
})
class UploadControllerWebMvcTest {

	private static final Path UPLOAD_DIRECTORY = Path.of("target/test-uploads");

	@Autowired
	private MockMvcTester mvc;

	@AfterEach
	void cleanUp() throws Exception {
		if (!Files.isDirectory(UPLOAD_DIRECTORY)) {
			return;
		}
		try (var files = Files.list(UPLOAD_DIRECTORY)) {
			for (Path file : files.toList()) {
				Files.deleteIfExists(file);
			}
		}
		Files.deleteIfExists(UPLOAD_DIRECTORY);
	}

	@Test
	void uploadSmallFileReturnsCreatedWithLocation() {
		MockMultipartFile file = new MockMultipartFile(
				"file", "hello.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes(StandardCharsets.UTF_8));
		MockMultipartFile note = new MockMultipartFile(
				"note", "", MediaType.TEXT_PLAIN_VALUE, "hi".getBytes(StandardCharsets.UTF_8));

		MvcTestResult result = this.mvc.post().multipart().uri("/upload")
				.file(file)
				.file(note)
				.exchange();

		assertThat(result).hasStatus(201);
		String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
		assertThat(location).startsWith("/files/");
		assertThat(UUID.fromString(location.substring("/files/".length()))).isNotNull();
	}

	@Test
	void downloadReturnsAttachmentWithExactBytes() {
		byte[] content = "download me".getBytes(StandardCharsets.UTF_8);
		MockMultipartFile file = new MockMultipartFile(
				"file", "report.txt", MediaType.TEXT_PLAIN_VALUE, content);

		MvcTestResult upload = this.mvc.post().multipart().uri("/upload")
				.file(file)
				.exchange();
		String location = upload.getResponse().getHeader(HttpHeaders.LOCATION);

		MvcTestResult download = this.mvc.get().uri(location)
				.exchange();

		assertThat(download).hasStatusOk();
		assertThat(download.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
				.contains("attachment")
				.contains("report.txt");
		assertThat(download.getResponse().getContentType()).startsWith(MediaType.TEXT_PLAIN_VALUE);
		assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(content);
	}

	@Test
	void uploadLargerThanConfiguredLimitIsRejected() {
		byte[] content = new byte[1025];
		MockMultipartFile file = new MockMultipartFile(
				"file", "too-large.txt", MediaType.TEXT_PLAIN_VALUE, content);

		MvcTestResult result = this.mvc.post().multipart().uri("/upload")
				.file(file)
				.exchange();

		assertThat(result.getResponse().getStatus() / 100).isNotEqualTo(2);
	}

}
