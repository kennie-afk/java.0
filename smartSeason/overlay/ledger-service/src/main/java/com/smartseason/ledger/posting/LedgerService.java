package com.smartseason.ledger.posting;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LedgerService {

    private final AccountRepository accounts;
    private final JournalEntryRepository journals;
    private final PostingRepository postings;
    private final AccountBalanceRepository balances;
    private final EventPublisher events;

    public LedgerService(AccountRepository accounts,
                         JournalEntryRepository journals,
                         PostingRepository postings,
                         AccountBalanceRepository balances,
                         EventPublisher events) {
        this.accounts = accounts;
        this.journals = journals;
        this.postings = postings;
        this.balances = balances;
        this.events = events;
    }

    @Transactional
    public JournalEntry post(PostingRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        var existing = journals.findByIdempotencyKeyAndTenantId(request.idempotencyKey(), tenantId);
        if (existing.isPresent()) {
            return existing.get();
        }

        BigDecimal debits = total(request, Posting.Direction.DEBIT);
        BigDecimal credits = total(request, Posting.Direction.CREDIT);

        if (debits.compareTo(credits) != 0) {
            throw new DomainRuleException(
                    "Journal entry does not balance: debits %s, credits %s"
                            .formatted(debits.toPlainString(), credits.toPlainString()));
        }
        if (debits.signum() == 0) {
            throw new DomainRuleException("Journal entry must move a non-zero amount");
        }

        List<Account> resolved = new ArrayList<>();
        for (PostingRequest.Line line : request.lines()) {
            Account account = accounts
                    .findByAccountCodeAndTenantId(line.accountCode(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Account " + line.accountCode() + " not found"));

            if (account.getStatus() == Account.Status.CLOSED) {
                throw new DomainRuleException("Account " + line.accountCode() + " is closed");
            }
            if (!account.getCurrency().equals(request.currency())) {
                throw new DomainRuleException(
                        "Account %s is denominated in %s but the entry is in %s"
                                .formatted(line.accountCode(), account.getCurrency(),
                                        request.currency()));
            }
            resolved.add(account);
        }

        JournalEntry entry = new JournalEntry();
        entry.setTenantId(tenantId);
        entry.setEntryNumber(nextEntryNumber(tenantId));
        entry.setDescription(request.description());
        entry.setSourceEvent(request.sourceEvent());
        entry.setSourceRef(request.sourceRef());
        entry.setPostedAt(Instant.now());
        entry.setEffectiveDate(request.effectiveDate());
        entry.setCurrency(request.currency());
        entry.setTotalDebit(debits);
        entry.setTotalCredit(credits);
        entry.setBalanced(true);
        entry.setIdempotencyKey(request.idempotencyKey());
        JournalEntry saved = journals.save(entry);

        for (int i = 0; i < request.lines().size(); i++) {
            PostingRequest.Line line = request.lines().get(i);
            Account account = resolved.get(i);

            Posting posting = new Posting();
            posting.setTenantId(tenantId);
            posting.setJournalEntryId(saved.getId());
            posting.setAccountId(account.getId());
            posting.setAccountCode(account.getAccountCode());
            posting.setDirection(line.direction());
            posting.setAmount(line.amount());
            posting.setCurrency(request.currency());
            posting.setPostedAt(saved.getPostedAt());
            posting.setMemo(line.memo());
            postings.save(posting);

            applyToBalance(tenantId, account, line.direction(), line.amount(), saved.getPostedAt());
        }

        events.publish("money", "JournalPosted", saved.getId(), saved.getEntryNumber());
        return saved;
    }

    private void applyToBalance(UUID tenantId, Account account, Posting.Direction direction,
                                BigDecimal amount, Instant postedAt) {
        AccountBalance balance = balances
                .findByAccountIdAndTenantId(account.getId(), tenantId)
                .orElseGet(() -> {
                    AccountBalance fresh = new AccountBalance();
                    fresh.setTenantId(tenantId);
                    fresh.setAccountId(account.getId());
                    fresh.setAccountCode(account.getAccountCode());
                    fresh.setCurrency(account.getCurrency());
                    fresh.setDebitTotal(BigDecimal.ZERO);
                    fresh.setCreditTotal(BigDecimal.ZERO);
                    fresh.setBalance(BigDecimal.ZERO);
                    fresh.setPostingCount(0L);
                    return fresh;
                });

        if (direction == Posting.Direction.DEBIT) {
            balance.setDebitTotal(balance.getDebitTotal().add(amount));
        } else {
            balance.setCreditTotal(balance.getCreditTotal().add(amount));
        }

        BigDecimal signed = balance.getDebitTotal().subtract(balance.getCreditTotal());
        balance.setBalance(account.getNormalBalance() == Account.NormalBalance.DEBIT
                ? signed
                : signed.negate());
        balance.setPostingCount(balance.getPostingCount() + 1);
        balance.setLastPostedAt(postedAt);
        balances.save(balance);
    }

    private static BigDecimal total(PostingRequest request, Posting.Direction direction) {
        return request.lines().stream()
                .filter(line -> line.direction() == direction)
                .map(PostingRequest.Line::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String nextEntryNumber(UUID tenantId) {
        return "JE-" + (journals.countByTenantId(tenantId) + 1);
    }
}
