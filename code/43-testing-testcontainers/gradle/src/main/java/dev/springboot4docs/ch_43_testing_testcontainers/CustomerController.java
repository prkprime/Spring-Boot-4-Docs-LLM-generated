package dev.springboot4docs.ch_43_testing_testcontainers;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    List<CustomerResponse> list() {
        return this.customers.list().stream().map(CustomerResponse::from).toList();
    }

    @GetMapping("/{id}")
    CustomerResponse get(@PathVariable long id) {
        return CustomerResponse.from(this.customers.get(id));
    }

    @PostMapping
    ResponseEntity<CustomerResponse> create(@RequestBody CustomerRequest request) {
        Customer customer = this.customers.create(request.email());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(customer.getId())
            .toUri();
        return ResponseEntity.created(location).body(CustomerResponse.from(customer));
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<Void> customerNotFound() {
        return ResponseEntity.notFound().build();
    }

    public record CustomerRequest(String email) {
    }

    public record CustomerResponse(Long id, String email, Instant createdAt) {

        static CustomerResponse from(Customer customer) {
            return new CustomerResponse(customer.getId(), customer.getEmail(), customer.getCreatedAt());
        }

    }

}
