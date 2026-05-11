package dev.springboot4docs.ch_20_transactions;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class BankAccount {

	@Id
	@GeneratedValue
	private Long id;

	private String owner;

	private long balanceCents;

	protected BankAccount() {
	}

	public BankAccount(String owner, long balanceCents) {
		this.owner = owner;
		this.balanceCents = balanceCents;
	}

	public Long getId() {
		return id;
	}

	public String getOwner() {
		return owner;
	}

	public long getBalanceCents() {
		return balanceCents;
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
