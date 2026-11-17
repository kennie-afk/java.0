package com.smartseason.agronomy.casework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartseason.agronomy.casework.CaseDtos.*;
import com.smartseason.agronomy.domain.Advisory;
import com.smartseason.agronomy.platform.ConflictException;
import com.smartseason.agronomy.platform.DomainRuleException;
import com.smartseason.agronomy.platform.ResourceNotFoundException;
import com.smartseason.agronomy.platform.TenantContext;
import com.smartseason.agronomy.repo.AdvisoryRepository;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The whole farmer-to-agronomist flow on a real (H2) database, with identity and mail stubbed. */
@SpringBootTest
class CaseFlowTest {

    @Autowired CaseService service;
    @Autowired CaseRepository cases;
    @Autowired AgronomistProfileRepository profiles;
    @Autowired AdvisoryRepository advisories;
    @MockitoBean IdentityDirectory identity;
    @MockitoBean CaseMailer mailer;

    UUID tenant;
    Caller farmer;
    Caller worker;
    Caller agro1;
    Caller agro2;

    private static Caller caller(String... roles) {
        return new Caller(UUID.randomUUID(), Set.of(roles));
    }

    private void person(Caller c, String name) {
        when(identity.me(c.id())).thenReturn(new IdentityDirectory.Person(c.id(), name.toLowerCase() + "@farm.test", name));
    }

    @BeforeEach
    void setUp() {
        reset(mailer);
        tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        farmer = caller("FARMER");
        worker = caller("WORKER");
        agro1 = caller("AGRONOMIST");
        agro2 = caller("AGRONOMIST");
        person(farmer, "Wanjiru");
        person(worker, "Otieno");
        person(agro1, "Achieng");
        person(agro2, "Kimani");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private RaiseRequest ask(String county, Advisory.Severity severity) {
        return new RaiseRequest(null, null, "maize", county, "Leaves have grey streaks", null,
                "MODEL", "Gray leaf spot, 41% confident", 0.41, severity, null);
    }

    private ProfileView enrol(Caller agronomist, String counties, int max) {
        service.myProfile(agronomist);
        return service.updateProfile(agronomist, new ProfileRequest(List.of(counties.split(",")), max, true));
    }

    @Test
    void withNoAgronomistTheCaseWaitsInTheSharedQueue() {
        CaseDetail d = service.raise(farmer, ask("Nakuru", Advisory.Severity.MEDIUM));
        assertThat(d.summary().status()).isEqualTo(AdvisoryCase.Status.OPEN);
        assertThat(d.summary().assignedTo()).isNull();
        assertThat(d.canClaim()).isFalse(); // the farmer cannot claim their own case
        CasePage queue = service.queue(agro1, CaseService.Scope.UNASSIGNED, null, 20);
        assertThat(queue.items()).extracting(CaseView::id).contains(d.summary().id());
        verifyNoInteractions(mailer);
    }

    @Test
    void aNewCaseGoesToTheLeastLoadedAgronomistWhoCoversTheCountyAndTheyAreEmailed() {
        enrol(agro1, "nakuru", 25);
        enrol(agro2, "nakuru", 25);
        // Give agro1 a case so agro2 is the less loaded.
        CaseDetail first = service.raise(farmer, ask("Nakuru", Advisory.Severity.MEDIUM));
        UUID firstOwner = first.summary().assignedTo();
        CaseDetail second = service.raise(farmer, ask("Nakuru", Advisory.Severity.MEDIUM));
        assertThat(second.summary().assignedTo()).isNotEqualTo(firstOwner);
        assertThat(second.summary().status()).isEqualTo(AdvisoryCase.Status.ASSIGNED);
        verify(mailer, org.mockito.Mockito.times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void acaseForACountyNobodyCoversIsNotForcedOnSomeone() {
        enrol(agro1, "kisumu", 25);
        CaseDetail d = service.raise(farmer, ask("Nakuru", Advisory.Severity.MEDIUM));
        assertThat(d.summary().status()).isEqualTo(AdvisoryCase.Status.OPEN);
    }

    @Test
    void whenTwoAgronomistsClaimAtOnceExactlyOneWins() throws Exception {
        CaseDetail d = service.raise(farmer, ask("Nakuru", Advisory.Severity.HIGH));
        UUID id = d.summary().id();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Callable<String>> tasks = new ArrayList<>();
        for (Caller who : List.of(agro1, agro2)) {
            tasks.add(() -> {
                TenantContext.set(tenant);
                go.await();
                try {
                    service.claim(who, id);
                    return "won";
                } catch (ConflictException ex) {
                    return "lost";
                } finally {
                    TenantContext.clear();
                }
            });
        }
        List<Future<String>> futures = new ArrayList<>();
        for (Callable<String> t : tasks) {
            futures.add(pool.submit(t));
        }
        go.countDown();
        List<String> results = new ArrayList<>();
        for (Future<String> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        assertThat(results).containsExactlyInAnyOrder("won", "lost");
        AdvisoryCase stored = cases.findByIdAndTenantId(id, tenant).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AdvisoryCase.Status.ASSIGNED);
        assertThat(stored.getAssignedTo()).isIn(agro1.id(), agro2.id());
    }

    @Test
    void claimingACaseThatIsAlreadyTakenIsRefusedButReclaimingYourOwnIsHarmless() {
        UUID id = service.raise(farmer, ask("Nakuru", Advisory.Severity.HIGH)).summary().id();
        service.claim(agro1, id);
        assertThat(service.claim(agro1, id).summary().assignedTo()).isEqualTo(agro1.id());
        assertThatThrownBy(() -> service.claim(agro2, id)).isInstanceOf(ConflictException.class);
    }

    @Test
    void anAgronomistReplyAnswersTheCaseAndFlagsItUnreadForTheFarmer() {
        enrol(agro1, "nakuru", 25);
        UUID id = service.raise(farmer, ask("Nakuru", Advisory.Severity.MEDIUM)).summary().id();
        reset(mailer);

        CaseDetail answered = service.reply(agro1, id, "Send me a close-up of the underside of the leaf.");
        assertThat(answered.summary().status()).isEqualTo(AdvisoryCase.Status.ANSWERED);
        assertThat(service.summary(farmer).attention()).isEqualTo(1);
        verify(mailer).send(eq("wanjiru@farm.test"), anyString(), any());

        // Opening it clears the badge.
        service.get(farmer, id);
        assertThat(service.summary(farmer).attention()).isZero();

        // The farmer's follow-up reopens it for the agronomist.
        CaseDetail reopened = service.reply(farmer, id, "Here is another photo.");
        assertThat(reopened.summary().status()).isEqualTo(AdvisoryCase.Status.ASSIGNED);
        assertThat(service.summary(agro1).attention()).isEqualTo(1);
        assertThat(reopened.messages()).hasSize(2);
    }

    @Test
    void anAgronomistCannotAnswerAnotherAgronomistsCase() {
        enrol(agro1, "nakuru", 25);
        UUID id = service.raise(farmer, ask("Nakuru", Advisory.Severity.MEDIUM)).summary().id();
        assertThatThrownBy(() -> service.reply(agro2, id, "I will take this"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void resolvingIssuesARealAdvisoryOnTheFarmersPlot() {
        UUID plot = UUID.randomUUID();
        UUID season = UUID.randomUUID();
        CaseDetail d = service.raise(farmer, new RaiseRequest(plot, season, "BEANS", "Nakuru",
                "Pods are spotted", null, "RULE", null, null, null, null));
        CaseDetail done = service.resolve(agro1, d.summary().id(), new ResolveRequest(
                "Angular leaf spot", "Remove infected plants, avoid working the field when wet.", Advisory.Severity.HIGH));
        assertThat(done.advisoryId()).isNotNull();
        Advisory a = advisories.findByIdAndTenantId(done.advisoryId(), tenant).orElseThrow();
        assertThat(a.getSource()).isEqualTo(Advisory.Source.AGRONOMIST);
        assertThat(a.getPlotId()).isEqualTo(plot);
        assertThat(a.getSeasonId()).isEqualTo(season);
        assertThat(a.getCropCode()).isEqualTo("BEANS");
        assertThat(done.summary().status()).isEqualTo(AdvisoryCase.Status.ANSWERED);
    }

    @Test
    void closedCasesRejectFurtherReplies() {
        UUID id = service.raise(farmer, ask("Nakuru", Advisory.Severity.LOW)).summary().id();
        service.close(farmer, id);
        assertThatThrownBy(() -> service.reply(farmer, id, "one more thing")).isInstanceOf(DomainRuleException.class);
    }

    @Test
    void aWorkerCannotSeeAnotherPersonsCaseAndItLooksLikeItDoesNotExist() {
        UUID id = service.raise(farmer, ask("Nakuru", Advisory.Severity.LOW)).summary().id();
        assertThatThrownBy(() -> service.get(worker, id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.queue(worker, CaseService.Scope.UNASSIGNED, null, 10))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void oneOrganisationNeverSeesAnothersCases() {
        UUID id = service.raise(farmer, ask("Nakuru", Advisory.Severity.LOW)).summary().id();
        TenantContext.set(UUID.randomUUID());
        assertThatThrownBy(() -> service.get(farmer, id)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.queue(agro1, CaseService.Scope.UNASSIGNED, null, 20).items()).isEmpty();
    }

    @Test
    void theQueueIsPagedByCursorInUrgencyOrderWithNothingMissedOrRepeated() {
        Advisory.Severity[] mix = {Advisory.Severity.LOW, Advisory.Severity.HIGH, Advisory.Severity.MEDIUM,
                Advisory.Severity.HIGH, Advisory.Severity.LOW, Advisory.Severity.MEDIUM, Advisory.Severity.HIGH};
        for (Advisory.Severity s : mix) {
            service.raise(farmer, ask("Nakuru", s));
        }
        List<CaseView> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            CasePage page = service.queue(agro1, CaseService.Scope.UNASSIGNED, cursor, 3);
            seen.addAll(page.items());
            cursor = page.next();
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).hasSize(mix.length);
        assertThat(seen).extracting(CaseView::id).doesNotHaveDuplicates();
        List<CaseView> expected = new ArrayList<>(seen);
        expected.sort(Comparator.comparingInt(CaseView::priority).reversed()
                .thenComparing(CaseView::createdAt).thenComparing(CaseView::id));
        assertThat(seen).containsExactlyElementsOf(expected);
        assertThat(seen.getFirst().priority()).isEqualTo(3);
    }

    @Test
    void photosAreStoredAndLimitedInCountSizeAndType() {
        String tiny = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
        RaiseRequest ok = new RaiseRequest(null, null, "MAIZE", "Nakuru", "See photos", null, null, null, null, null,
                List.of(new PhotoIn("image/jpeg", tiny), new PhotoIn("image/png", tiny)));
        CaseDetail d = service.raise(farmer, ok);
        assertThat(d.summary().photoCount()).isEqualTo(2);
        assertThat(service.get(farmer, d.summary().id()).photos()).hasSize(2);
        UUID photo = service.get(farmer, d.summary().id()).photos().getFirst().id();
        assertThat(service.photo(farmer, d.summary().id(), photo).getData()).containsExactly(1, 2, 3);

        List<PhotoIn> four = List.of(new PhotoIn("image/jpeg", tiny), new PhotoIn("image/jpeg", tiny),
                new PhotoIn("image/jpeg", tiny), new PhotoIn("image/jpeg", tiny));
        assertThatThrownBy(() -> service.raise(farmer, new RaiseRequest(null, null, null, null, "x y z", null, null,
                null, null, null, four))).isInstanceOf(DomainRuleException.class);
        assertThatThrownBy(() -> service.raise(farmer, new RaiseRequest(null, null, null, null, "x y z", null, null,
                null, null, null, List.of(new PhotoIn("application/pdf", tiny))))).isInstanceOf(DomainRuleException.class);
        String huge = Base64.getEncoder().encodeToString(new byte[CaseService.MAX_PHOTO_BYTES + 1]);
        assertThatThrownBy(() -> service.raise(farmer, new RaiseRequest(null, null, null, null, "x y z", null, null,
                null, null, null, List.of(new PhotoIn("image/jpeg", huge))))).isInstanceOf(DomainRuleException.class);
    }

    @Test
    void similarOpenCasesInTheSameCountyAndCropAreCountedForTheAgronomist() {
        service.raise(farmer, ask("Nakuru", Advisory.Severity.MEDIUM));
        service.raise(worker, ask("Nakuru", Advisory.Severity.MEDIUM));
        UUID third = service.raise(farmer, ask("Nakuru", Advisory.Severity.MEDIUM)).summary().id();
        assertThat(service.get(agro1, third).similarOpen()).isEqualTo(2);
    }

    @Test
    void anAgronomistProfileCanBeSetUpAndRejectsAbsurdLimits() {
        ProfileView p = enrol(agro1, "nakuru,kiambu", 10);
        assertThat(p.counties()).containsExactly("nakuru", "kiambu");
        assertThat(p.email()).isEqualTo("achieng@farm.test");
        assertThatThrownBy(() -> service.updateProfile(agro1, new ProfileRequest(null, 0, null)))
                .isInstanceOf(DomainRuleException.class);
    }
}
