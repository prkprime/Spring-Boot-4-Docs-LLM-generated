package dev.springboot4docs.ch_20_transactions;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

	private final BankAccountRepository accounts;

	public TransferService(BankAccountRepository accounts) {
		this.accounts = accounts;
	}

	@Transactional
	public void transfer(long fromId, long toId, long cents) {
		moveMoney(fromId, toId, cents);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void transferRequiresNew(long fromId, long toId, long cents) {
		moveMoney(fromId, toId, cents);
	}

	@Transactional(readOnly = true)
	public BankAccount get(long id) {
		return accounts.findById(id).orElseThrow();
	}

	private void moveMoney(long fromId, long toId, long cents) {
		var from = accounts.findById(fromId).orElseThrow();
		var to = accounts.findById(toId).orElseThrow();

		from.withdraw(cents);
		to.deposit(cents);
	}

}
