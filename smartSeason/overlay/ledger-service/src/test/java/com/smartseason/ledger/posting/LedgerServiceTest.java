package com.smartseason.ledger.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.ledger.domain.Account;
import com.smartseason.ledger.domain.AccountBalance;
import com.smartseason.ledger.domain.JournalEntry;
import com.smartseason.ledger.domain.Posting;
import com.smartseason.ledger.platform.DomainRuleException;
import com.smartseason.ledger.platform.EventPublisher;
import com.smartseason.ledger.platform.ResourceNotFoundException;
import com.smartseason.ledger.platform.TenantContext;
import com.smartseason.ledger.repo.AccountBalanceRepository;
import com.smartseason.ledger.repo.AccountRepository;
import com.smartseason.ledger.repo.JournalEntryRepository;
import com.smartseason.ledger.repo.PostingRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LedgerServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final JournalEntryRepository journals = mock(JournalEntryRepository.class);
    private final PostingRepository postings = mock(PostingRepository.class);
    private final AccountBalanceRepository balances = mock(AccountBalanceRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);

    private final LedgerService service =
            new LedgerService(accounts, journals, postings, balances, events);

    private final UUID tenant = UUID.randomUUID();
    private final List<AccountBalance> savedBalances = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContext.set(tenant);
        savedBalances.clear();

        when(journals.findByIdempotencyKeyAndTenantId(any(), eq(tenant)))
                .thenReturn(Optional.empty());
        when(journals.countByTenantId(tenant)).thenReturn(0L);
        when(journals.save(any(JournalEntry.class))).thenAnswer(invocation -> {
            JournalEntry entry = invocation.getArgument(0);
            if (entry.getId() == null) {
                entry.setId(UUID.randomUUID());
            }
            return entry;
        });
        when(postings.save(any(Posting.class))).thenAnswer(i -> i.getArgument(0));
        when(balances.findByAccountIdAndTenantId(any(), eq(tenant))).thenReturn(Optional.empty());
        when(balances.save(any(AccountBalance.class))).thenAnswer(invocation -> {
            AccountBalance balance = invocation.getArgument(0);
            savedBalances.add(balance);
            return balance;
        });

        stubAccount("1000", Account.AccountType.ASSET, Account.NormalBalance.DEBIT, "KES");
        stubAccount("4000", Account.AccountType.REVENUE, Account.NormalBalance.CREDIT, "KES");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Account stubAccount(String code, Account.AccountType type,
                                Account.NormalBalance normal, String currency) {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setTenantId(tenant);
        account.setAccountCode(code);
        account.setName("Account " + code);
        account.setAccountType(type);
        account.setNormalBalance(normal);
        account.setCurrency(currency);
        account.setStatus(Account.Status.ACTIVE);
        when(accounts.findByAccountCodeAndTenantId(code, tenant)).thenReturn(Optional.of(account));
        return account;
    }

    private PostingRequest entry(String key, List<PostingRequest.Line> lines) {
        return new PostingRequest("Produce sale", "KES", LocalDate.of(2026, 3, 2),
                "OrderPaid", "order-123", key, lines);
    }

    private PostingRequest balancedEntry() {
        return entry("idem-1", List.of(
                new PostingRequest.Line("1000", Posting.Direction.DEBIT, new BigDecimal("5000.00"), "cash in"),
                new PostingRequest.Line("4000", Posting.Direction.CREDIT, new BigDecimal("5000.00"), "sale")));
    }

    @Test
    @DisplayName("a balanced entry posts and records both sides")
    void balancedEntryPosts() {
        JournalEntry saved = service.post(balancedEntry());

        assertThat(saved.getBalanced()).isTrue();
        assertThat(saved.getTotalDebit()).isEqualByComparingTo("5000.00");
        assertThat(saved.getTotalCredit()).isEqualByComparingTo("5000.00");
        assertThat(saved.getEntryNumber()).isEqualTo("JE-1");

        ArgumentCaptor<Posting> captor = ArgumentCaptor.forClass(Posting.class);
        verify(postings, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Posting::getDirection)
                .containsExactly(Posting.Direction.DEBIT, Posting.Direction.CREDIT);

        verify(events).publish(eq("money"), eq("JournalPosted"), any(), any());
    }

    @Test
    @DisplayName("an unbalanced entry is refused and nothing is written")
    void unbalancedEntryRefused() {
        PostingRequest request = entry("idem-2", List.of(
                new PostingRequest.Line("1000", Posting.Direction.DEBIT, new BigDecimal("5000.00"), null),
                new PostingRequest.Line("4000", Posting.Direction.CREDIT, new BigDecimal("4999.99"), null)));

        assertThatThrownBy(() -> service.post(request))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("does not balance");

        verify(journals, never()).save(any());
        verify(postings, never()).save(any());
        verify(events, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a zero-value entry is refused")
    void zeroValueRefused() {
        PostingRequest request = entry("idem-3", List.of(
                new PostingRequest.Line("1000", Posting.Direction.DEBIT, new BigDecimal("0.0000"), null),
                new PostingRequest.Line("4000", Posting.Direction.CREDIT, new BigDecimal("0.0000"), null)));

        assertThatThrownBy(() -> service.post(request))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("non-zero");
    }

    @Test
    @DisplayName("replaying the same idempotency key returns the original entry without double-posting")
    void replayIsIdempotent() {
        JournalEntry original = new JournalEntry();
        original.setId(UUID.randomUUID());
        original.setEntryNumber("JE-1");
        when(journals.findByIdempotencyKeyAndTenantId("idem-1", tenant))
                .thenReturn(Optional.of(original));

        JournalEntry returned = service.post(balancedEntry());

        assertThat(returned).isSameAs(original);
        verify(journals, never()).save(any());
        verify(postings, never()).save(any());
    }

    @Test
    @DisplayName("posting to an unknown account fails before anything is written")
    void unknownAccountRejected() {
        when(accounts.findByAccountCodeAndTenantId("9999", tenant)).thenReturn(Optional.empty());

        PostingRequest request = entry("idem-4", List.of(
                new PostingRequest.Line("1000", Posting.Direction.DEBIT, new BigDecimal("10.00"), null),
                new PostingRequest.Line("9999", Posting.Direction.CREDIT, new BigDecimal("10.00"), null)));

        assertThatThrownBy(() -> service.post(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(journals, never()).save(any());
    }

    @Test
    @DisplayName("a currency mismatch between entry and account is refused")
    void currencyMismatchRejected() {
        stubAccount("2000", Account.AccountType.LIABILITY, Account.NormalBalance.CREDIT, "USD");

        PostingRequest request = entry("idem-5", List.of(
                new PostingRequest.Line("1000", Posting.Direction.DEBIT, new BigDecimal("10.00"), null),
                new PostingRequest.Line("2000", Posting.Direction.CREDIT, new BigDecimal("10.00"), null)));

        assertThatThrownBy(() -> service.post(request))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("denominated in USD");

        verify(journals, never()).save(any());
    }

    @Test
    @DisplayName("a closed account cannot be posted to")
    void closedAccountRejected() {
        Account closed = stubAccount("3000", Account.AccountType.EQUITY,
                Account.NormalBalance.CREDIT, "KES");
        closed.setStatus(Account.Status.CLOSED);

        PostingRequest request = entry("idem-6", List.of(
                new PostingRequest.Line("1000", Posting.Direction.DEBIT, new BigDecimal("10.00"), null),
                new PostingRequest.Line("3000", Posting.Direction.CREDIT, new BigDecimal("10.00"), null)));

        assertThatThrownBy(() -> service.post(request))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("is closed");
    }

    @Test
    @DisplayName("balances follow each account's normal side, so both sides read positive")
    void balancesRespectNormalSide() {
        service.post(balancedEntry());

        assertThat(savedBalances).hasSize(2);

        AccountBalance asset = savedBalances.stream()
                .filter(b -> b.getAccountCode().equals("1000")).findFirst().orElseThrow();
        AccountBalance revenue = savedBalances.stream()
                .filter(b -> b.getAccountCode().equals("4000")).findFirst().orElseThrow();

        assertThat(asset.getBalance())
                .as("a debit increases a debit-normal asset")
                .isEqualByComparingTo("5000.00");
        assertThat(revenue.getBalance())
                .as("a credit increases a credit-normal revenue account")
                .isEqualByComparingTo("5000.00");
        assertThat(asset.getPostingCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a multi-line entry balances across more than two accounts")
    void multiLineEntryBalances() {
        stubAccount("5000", Account.AccountType.EXPENSE, Account.NormalBalance.DEBIT, "KES");

        PostingRequest request = entry("idem-7", List.of(
                new PostingRequest.Line("1000", Posting.Direction.DEBIT, new BigDecimal("4500.00"), "net"),
                new PostingRequest.Line("5000", Posting.Direction.DEBIT, new BigDecimal("500.00"), "commission"),
                new PostingRequest.Line("4000", Posting.Direction.CREDIT, new BigDecimal("5000.00"), "gross")));

        JournalEntry saved = service.post(request);

        assertThat(saved.getTotalDebit()).isEqualByComparingTo("5000.00");
        assertThat(saved.getTotalCredit()).isEqualByComparingTo("5000.00");
        verify(postings, org.mockito.Mockito.times(3)).save(any());
    }
}
