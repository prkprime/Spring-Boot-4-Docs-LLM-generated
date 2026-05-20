package dev.springboot4docs.ch_20_transactions;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankAccount {

    @Id
    @GeneratedValue
    private Long id;

    private String owner;

    private long balanceCents;

    public BankAccount(String owner, long balanceCents) {
        this.owner = owner;
        this.balanceCents = balanceCents;
    }

    void withdraw(long cents) {
        if (balanceCents - cents < 0) {
            throw new InsufficientFundsException("Account %s does not have enough funds".formatted(id));
        }
        this.balanceCents -= cents;
    }

    void deposit(long cents) {
        this.balanceCents += cents;
    }

}
