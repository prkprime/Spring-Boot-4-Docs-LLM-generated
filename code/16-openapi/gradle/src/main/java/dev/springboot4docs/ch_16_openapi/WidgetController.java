package dev.springboot4docs.ch_16_openapi;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WidgetController {

    private final AtomicLong nextId = new AtomicLong(3);

    private final Map<Long, Widget> widgets = new ConcurrentHashMap<>(Map.of(
            1L, new Widget(1L, "Console"),
            2L, new Widget(2L, "Dashboard")));

    @Operation(summary = "List widgets", description = "Returns every widget currently known to the application.")
    @GetMapping("/widgets")
    List<Widget> list() {
        return new ArrayList<>(this.widgets.values());
    }

    @Operation(summary = "Get one widget")
    @ApiResponse(responseCode = "200", description = "Widget found")
    @ApiResponse(responseCode = "404", description = "Widget not found")
    @GetMapping("/widgets/{id}")
    ResponseEntity<Widget> get(
            @Parameter(description = "Widget identifier", example = "1")
            @PathVariable Long id) {
        return Optional.ofNullable(this.widgets.get(id))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create a widget")
    @ApiResponse(responseCode = "201", description = "Widget created")
    @PostMapping("/widgets")
    ResponseEntity<Widget> create(@RequestBody Widget request) {
        Long id = this.nextId.getAndIncrement();
        Widget created = new Widget(id, request.name());
        this.widgets.put(id, created);
        return ResponseEntity.created(URI.create("/widgets/" + id)).body(created);
    }

    record Widget(
            @Schema(description = "server-assigned identifier", example = "1")
            Long id,
            @Schema(description = "display name", example = "Dashboard")
            String name) {
    }

}
