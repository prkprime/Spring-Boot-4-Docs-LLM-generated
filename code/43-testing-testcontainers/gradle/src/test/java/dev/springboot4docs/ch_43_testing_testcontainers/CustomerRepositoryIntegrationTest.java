package dev.springboot4docs.ch_43_testing_testcontainers;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = NONE)
class CustomerRepositoryIntegrationTest {

    @Autowired
    private CustomerRepository customers;

    @Test
    void savesAndFindsCustomerInPostgres() {
        Customer saved = this.customers.saveAndFlush(new Customer("ada@example.com"));

        assertThat(this.customers.findByEmail("ada@example.com"))
            .hasValueSatisfying(customer -> {
                assertThat(customer.getId()).isEqualTo(saved.getId());
                assertThat(customer.getCreatedAt()).isNotNull();
            });
    }

    @Test
    void rollsBackBetweenDataJpaTestMethods() {
        assertThat(this.customers.findAll()).isEmpty();
    }

}
