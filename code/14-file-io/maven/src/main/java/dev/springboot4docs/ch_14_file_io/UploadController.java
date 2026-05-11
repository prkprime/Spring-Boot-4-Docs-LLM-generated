package dev.springboot4docs.ch_14_file_io;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
class UploadController {

	private final Path uploadDirectory;

	private final DataSize maxFileSize;

	UploadController(@Value("${app.upload-dir:target/uploads}") Path uploadDirectory,
			@Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) {
		this.uploadDirectory = uploadDirectory;
		this.maxFileSize = maxFileSize;
	}

	@PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<Void> upload(@RequestPart("file") MultipartFile file,
			@RequestPart(name = "note", required = false) String note) throws IOException {
		if (file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file must not be empty");
		}
		if (file.getSize() > this.maxFileSize.toBytes()) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "file is too large");
		}

		Files.createDirectories(this.uploadDirectory);
		UUID id = UUID.randomUUID();
		String filename = sanitizeFilename(file.getOriginalFilename());
		Path destination = this.uploadDirectory.resolve(id + "-" + filename);
		file.transferTo(destination);

		return ResponseEntity.created(URI.create("/files/" + id)).build();
	}

	@GetMapping("/files/{id}")
	ResponseEntity<Resource> download(@PathVariable UUID id) throws IOException {
		Path file = findFile(id);
		Resource resource = new FileSystemResource(file);

		return ResponseEntity.ok()
				.contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
				.contentLength(Files.size(file))
				.header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(file))
				.body(resource);
	}

	@GetMapping("/files/{id}/stream")
	ResponseEntity<StreamingResponseBody> stream(@PathVariable UUID id) throws IOException {
		Path file = findFile(id);
		StreamingResponseBody body = (outputStream) -> {
			byte[] buffer = new byte[8192];
			try (InputStream inputStream = Files.newInputStream(file)) {
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					outputStream.write(buffer, 0, bytesRead);
				}
			}
		};

		return ResponseEntity.ok()
				.contentType(MediaTypeFactory.getMediaType(file.getFileName().toString())
						.orElse(MediaType.APPLICATION_OCTET_STREAM))
				.contentLength(Files.size(file))
				.header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(file))
				.body(body);
	}

	private Path findFile(UUID id) throws IOException {
		if (!Files.isDirectory(this.uploadDirectory)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		String prefix = id + "-";
		try (var files = Files.list(this.uploadDirectory)) {
			return files
					.filter(Files::isRegularFile)
					.filter((file) -> file.getFileName().toString().startsWith(prefix))
					.findFirst()
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		}
	}

	private String attachmentHeader(Path file) {
		String filename = file.getFileName().toString();
		int separator = filename.indexOf('-');
		if (separator >= 0 && separator + 1 < filename.length()) {
			filename = filename.substring(separator + 1);
		}
		return ContentDisposition.attachment()
				.filename(filename, StandardCharsets.UTF_8)
				.build()
				.toString();
	}

	private String sanitizeFilename(String originalFilename) {
		String filename = StringUtils.hasText(originalFilename) ? originalFilename : "upload.bin";
		Path path = Paths.get(filename).getFileName();
		if (path != null) {
			filename = path.toString();
		}
		filename = filename.replaceAll("[\\\\/\\p{Cntrl}]", "_");
		return StringUtils.hasText(filename) ? filename : "upload.bin";
	}

}
