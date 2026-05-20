package dev.springboot4docs.ch_20_transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class TransferServiceIntegrationTest {

    private final BankAccountRepository accounts;

    private final LedgerService ledgerService;

    private final TransactionTemplate transactionTemplate;

    private final TransferService transferService;

    @Autowired
    TransferServiceIntegrationTest(BankAccountRepository accounts, LedgerService ledgerService,
            TransactionTemplate transactionTemplate, TransferService transferService) {
        this.accounts = accounts;
        this.ledgerService = ledgerService;
        this.transactionTemplate = transactionTemplate;
        this.transferService = transferService;
    }

    @BeforeEach
    void cleanDatabase() {
        transactionTemplate.executeWithoutResult((status) -> accounts.deleteAll());
    }

    @Test
    void transferMovesMoneyAndPersistsBothRows() {
        var ids = createAccounts();

        transferService.transfer(ids.alice(), ids.bob(), 2_500);

        var balances = readBalances(ids);

        assertThat(balances).containsExactly(7_500L, 5_000L);
        assertThat(readAccountCount()).isEqualTo(2);
    }

    @Test
    void insufficientFundsRollsBackTheWholeTransfer() {
        var ids = createAccounts();

        assertThatThrownBy(() -> transferService.transfer(ids.alice(), ids.bob(), 20_000))
                .isInstanceOf(InsufficientFundsException.class);

        var balances = readBalances(ids);

        assertThat(balances).containsExactly(10_000L, 2_500L);
        assertThat(readAccountCount()).isEqualTo(2);
    }

    @Test
    void requiresNewCommitSurvivesOuterRollback() {
        var ids = createAccounts();

        assertThatThrownBy(() -> ledgerService.recordTransferThenFail(ids.alice(), ids.bob(), 1_500))
                .isInstanceOf(IllegalStateException.class);

        var balances = readBalances(ids);
        var marker = transactionTemplate.execute(
                (status) -> accounts.findByOwner(LedgerService.OUTER_MARKER_OWNER));

        assertThat(balances).containsExactly(8_500L, 4_000L);
        assertThat(marker).isEmpty();
        assertThat(readAccountCount()).isEqualTo(2);
    }

    private AccountIds createAccounts() {
        return transactionTemplate.execute((status) -> {
            var alice = accounts.save(new BankAccount("Alice", 10_000));
            var bob = accounts.save(new BankAccount("Bob", 2_500));
            return new AccountIds(alice.getId(), bob.getId());
        });
    }

    private List<Long> readBalances(AccountIds ids) {
        return transactionTemplate.execute((status) -> List.of(
                accounts.findById(ids.alice()).orElseThrow().getBalanceCents(),
                accounts.findById(ids.bob()).orElseThrow().getBalanceCents()));
    }

    private long readAccountCount() {
        return transactionTemplate.execute((status) -> accounts.count());
    }

    private record AccountIds(long alice, long bob) {
    }

}
