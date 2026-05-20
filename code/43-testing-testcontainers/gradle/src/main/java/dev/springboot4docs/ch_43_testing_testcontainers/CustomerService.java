package dev.springboot4docs.ch_43_testing_testcontainers;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public List<Customer> list() {
        return this.customers.findAll();
    }

    @Transactional(readOnly = true)
    public Customer get(long id) {
        return this.customers.findById(id).orElseThrow(CustomerNotFoundException::new);
    }

    @Transactional
    public Customer create(String email) {
        return this.customers.save(new Customer(email));
    }

}
