package dev.springboot4docs.ch_20_transactions;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    static final String OUTER_MARKER_OWNER = "outer-ledger-marker";

    private final BankAccountRepository accounts;

    private final TransferService transfers;

    public LedgerService(BankAccountRepository accounts, TransferService transfers) {
        this.accounts = accounts;
        this.transfers = transfers;
    }

    @Transactional
    public void recordTransferThenFail(long fromId, long toId, long cents) {
        accounts.save(new BankAccount(OUTER_MARKER_OWNER, 0));

        transfers.transferRequiresNew(fromId, toId, cents);

        throw new IllegalStateException("The outer ledger write failed after the transfer committed");
    }

}
