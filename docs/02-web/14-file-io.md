# File Upload And Download

File endpoints have two halves. The server receives bytes from the client, usually as a multipart form upload. The server also sends bytes back, usually as a `Resource` or as a streaming response. Both directions look simple in a controller method, but the details matter: content type, content disposition, size limits, filenames, and whether a large file is buffered in memory.

This chapter keeps storage deliberately small. Uploaded files are written under `target/uploads` with a generated id in the filename. That is enough to show the HTTP and Spring MVC pieces without pretending this is a production storage layer.

## Multipart Uploads

Browsers send file uploads as `multipart/form-data`. A multipart request is one HTTP request body split into named parts. A typical form might contain one binary part named `file` and one text part named `note`:

```html
<form method="post" enctype="multipart/form-data" action="/upload">
  <input type="file" name="file">
  <input type="text" name="note">
  <button type="submit">Upload</button>
</form>
```

The `enctype` is not optional for browser forms. Without `multipart/form-data`, the browser does not send the selected file as a file part.

Spring MVC gives you a few ways to read multipart input. `MultipartFile` is the most common Spring abstraction for uploaded files. It gives you the original filename, content type, size, input stream, bytes, and `transferTo(...)`. The Servlet API also has `jakarta.servlet.http.Part`, which is closer to the underlying container API. For controller method arguments, `@RequestPart` means "bind this named multipart part", while `@RequestParam` means "bind this request parameter". Both can work for simple forms, but `@RequestPart` is the clearer choice when you are intentionally dealing with multipart parts, especially if one part is a file and another part has its own content type.

The sample upload endpoint accepts the file as a required part and the note as an optional text part:

```java
{% include-markdown "../../code/14-file-io/maven/src/main/java/dev/springboot4docs/ch_14_file_io/UploadController.java" comments=false %}
```

The important upload path is short:

```java
@PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
ResponseEntity<Void> upload(@RequestPart("file") MultipartFile file,
		@RequestPart(name = "note", required = false) String note) throws IOException {
```

The method rejects an empty file, checks the configured max file size, creates the upload directory, generates a UUID, sanitizes the original filename, and writes the file:

```java
UUID id = UUID.randomUUID();
String filename = sanitizeFilename(file.getOriginalFilename());
Path destination = this.uploadDirectory.resolve(id + "-" + filename);
file.transferTo(destination);
```

The response is `201 Created` with a `Location` header pointing to the new download URL:

```java
return ResponseEntity.created(URI.create("/files/" + id)).build();
```

That shape is useful for APIs because the upload response does not need to send the file back. It only tells the client where the saved file can be fetched.

## Size Limits

Spring Boot configures servlet multipart limits through properties. The chapter uses YAML:

```yaml
{% include-markdown "../../code/14-file-io/maven/src/main/resources/application.yml" comments=false %}
```

`max-file-size` limits one uploaded file part. `max-request-size` limits the whole multipart request, including all file parts, text fields, and multipart framing overhead. A request can be under the per-file limit and still exceed the request limit if it contains several files or a lot of form data.

In a deployed servlet application, the request may be rejected before your controller runs. Depending on where the failure happens, the client might see `413 Payload Too Large`, `400 Bad Request`, or a server-specific error response if you have not mapped the exception. That is the common gotcha: the limit is part of request parsing, not ordinary business validation.

The sample controller also has an explicit file-size guard:

```java
if (file.getSize() > this.maxFileSize.toBytes()) {
	throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "file is too large");
}
```

That keeps the example's MVC slice test deterministic. `MockMvcTester` builds a mock multipart request that is already parsed, so it does not exercise every servlet-container parsing path. The application property is still the real external limit. The controller guard makes the API contract visible in the web-layer test.

## Downloading A Resource

For normal file downloads, return a Spring `Resource`. A `FileSystemResource` points to a file on disk without forcing you to load the whole file into a byte array in controller code:

```java
Resource resource = new FileSystemResource(file);
```

The response sets three details deliberately:

```java
return ResponseEntity.ok()
		.contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
		.contentLength(Files.size(file))
		.header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(file))
		.body(resource);
```

`MediaTypeFactory.getMediaType(...)` picks a content type from the filename. A `.txt` file becomes `text/plain`; a `.pdf` file becomes `application/pdf`; unknown extensions fall back to `application/octet-stream`. You should still treat client-supplied file content as untrusted. This content type is a response hint, not proof that the file bytes are safe.

`Content-Disposition` tells the browser how to present the response. `attachment` means "download this as a file". `inline` means "the browser may display this in the page or tab if it knows how". A PDF can be either. A report export is usually `attachment`; an image preview might be `inline`.

The sample uses Spring's `ContentDisposition` builder:

```java
return ContentDisposition.attachment()
		.filename(filename, StandardCharsets.UTF_8)
		.build()
		.toString();
```

Passing `StandardCharsets.UTF_8` matters for non-ASCII filenames. It lets Spring render a `filename*` parameter using the HTTP encoding format understood by modern browsers. If you build this header by hand, it is easy to get quoting, spaces, semicolons, and non-ASCII characters wrong.

## Streaming Large Downloads

Returning a `Resource` is enough for many downloads. For very large files, generated exports, or data coming from another stream, `StreamingResponseBody` gives you direct control over writing chunks to the response:

```java
StreamingResponseBody body = (outputStream) -> {
	byte[] buffer = new byte[8192];
	try (InputStream inputStream = Files.newInputStream(file)) {
		int bytesRead;
		while ((bytesRead = inputStream.read(buffer)) != -1) {
			outputStream.write(buffer, 0, bytesRead);
		}
	}
};
```

The endpoint returns the same content type, length, and attachment header as the `Resource` endpoint:

```java
return ResponseEntity.ok()
		.contentType(MediaTypeFactory.getMediaType(file.getFileName().toString())
				.orElse(MediaType.APPLICATION_OCTET_STREAM))
		.contentLength(Files.size(file))
		.header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(file))
		.body(body);
```

The practical difference is memory behavior. Do not read a large file with `Files.readAllBytes(...)` just to return it from a controller. That allocates one big array for the whole file. The streaming endpoint copies a fixed-size buffer repeatedly, so memory usage stays stable as the file grows.

With `StreamingResponseBody`, Spring MVC starts asynchronous request processing. The original servlet request thread can return after the handler returns the `StreamingResponseBody`; the actual writing happens through MVC's async execution infrastructure. The response still uses servlet I/O, and the client still receives one HTTP response. You just avoid tying the controller method to a full in-memory body.

## Storage Safety

Never trust `MultipartFile.getOriginalFilename()` as a path. A malicious client can send names containing path separators, weird control characters, or names that collide with files you care about. The sample strips any path portion and replaces separators and control characters:

```java
Path path = Paths.get(filename).getFileName();
if (path != null) {
	filename = path.toString();
}
filename = filename.replaceAll("[\\\\/\\p{Cntrl}]", "_");
```

The user chooses a filename label, not a filesystem location. The server chooses the directory. The generated UUID keeps two uploads named `report.pdf` from overwriting each other.

Real systems usually go further. They store metadata in a database, scan files when the domain requires it, restrict allowed content types, avoid serving private files through guessable URLs, and may write to object storage instead of the local filesystem. Those are storage policy decisions. The MVC mechanics stay the same: receive a part, store bytes somewhere controlled, and return a stable id.

## The Tests

The test is a Spring Boot 4 MVC slice with `MockMvcTester`:

```java
{% include-markdown "../../code/14-file-io/maven/src/test/java/dev/springboot4docs/ch_14_file_io/UploadControllerWebMvcTest.java" comments=false %}
```

The slice overrides the upload directory so test files go under `target/test-uploads`, then deletes that directory after each test:

```java
@TestPropertySource(properties = {
		"app.upload-dir=target/test-uploads",
		"spring.servlet.multipart.max-file-size=1KB",
		"spring.servlet.multipart.max-request-size=2KB"
})
```

The multipart syntax is the main new testing shape:

```java
MvcTestResult result = this.mvc.post().multipart().uri("/upload")
		.file(file)
		.file(note)
		.exchange();
```

`post().multipart()` switches the tester to the multipart request builder. Each `MockMultipartFile` becomes one named multipart part. The file part is named `file` because the controller expects `@RequestPart("file")`. The note part is named `note` because the controller expects `@RequestPart(name = "note", required = false)`.

The first test uploads a small in-memory text file and asserts `201 Created` plus a `Location` header beginning with `/files/`. It also parses the suffix as a UUID, which proves the URL has the expected id shape.

The second test uploads bytes, follows the returned location with `GET`, and asserts the exact response body bytes:

```java
assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(content);
```

That is better than only checking status. A file endpoint can return `200 OK` while corrupting bytes, changing encodings, or returning an error page as a download.

The size-limit test uses the test override of `1KB` and sends `1025` bytes. That keeps the test quick while still proving oversized uploads are rejected:

```java
assertThat(result.getResponse().getStatus() / 100).isNotEqualTo(2);
```

The assertion intentionally allows any non-2xx status. In production you should usually map oversized uploads to a clean `413`, but framework and container behavior can differ depending on whether the request is rejected during parsing, controller execution, or exception handling.

Run the tests from the chapter project:

```bash
./mvnw -q -B test
```

## Run It

Start the app:

```bash
./mvnw spring-boot:run
```

Upload a file and a text note:

```bash
curl -i -F file=@local.txt -F note=hi http://localhost:8080/upload
```

The response includes a location:

```http
HTTP/1.1 201
Location: /files/7f9f2f8a-3c7d-4f44-9f76-2df7c4d51b8a
```

Download the file using curl's remote-header filename support:

```bash
curl -OJ http://localhost:8080/files/7f9f2f8a-3c7d-4f44-9f76-2df7c4d51b8a
```

Use the streaming endpoint the same way when you want the chunked controller path:

```bash
curl -OJ http://localhost:8080/files/7f9f2f8a-3c7d-4f44-9f76-2df7c4d51b8a/stream
```

Chapter 15 builds on this by moving from individual HTTP file flows to richer web application concerns.
