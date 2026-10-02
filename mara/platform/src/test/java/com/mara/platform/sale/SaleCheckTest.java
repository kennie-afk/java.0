package com.mara.platform.sale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SaleCheckTest {

    private static SaleBody sale(String unit, String net, String tax, String total, String applied, int taxBp) {
        return new SaleBody(SaleBody.V1, "KES",
                List.of(new SaleBody.Line("SKU", "Item", 2, unit, taxBp, net, tax)),
                List.of(new SaleBody.Payment("CASH", applied, "", applied)),
                total, new SaleBody.Fiscal(SaleBody.Fiscal.PENDING, ""), null);
    }

    @Test
    void aCorrectSaleHasNoFindings() {
        SaleCheck.Result r = SaleCheck.check(sale("7000", "14000", "2240", "16240", "16240", 1600));
        assertTrue(r.consistent(), r.findings().toString());
        assertEquals(14000, r.netMinor());
        assertEquals(2240, r.taxMinor());
    }

    @Test
    void taxRoundsHalfUpPerLine() {
        // 2 x 1,25 = 2.50 at 16% is 40 exactly; 2 x 3.03 net 606 at 16% = 96.96 -> 97
        SaleCheck.Result r = SaleCheck.check(sale("303", "606", "97", "703", "703", 1600));
        assertTrue(r.consistent(), r.findings().toString());
        SaleCheck.Result bad = SaleCheck.check(sale("303", "606", "96", "702", "702", 1600));
        assertEquals(List.of(SaleCheck.Finding.LINE_TAX_MISMATCH), bad.findings());
    }

    @Test
    void shortPaymentAndWrongTotalAreBothFlagged() {
        SaleCheck.Result r = SaleCheck.check(sale("7000", "14000", "2240", "16000", "15000", 1600));
        assertTrue(r.findings().contains(SaleCheck.Finding.TOTAL_MISMATCH));
        assertTrue(r.findings().contains(SaleCheck.Finding.PAYMENTS_DO_NOT_SETTLE_TOTAL));
    }

    @Test
    void nonNumericAmountsAreMalformedNotACrash() {
        assertEquals(List.of(SaleCheck.Finding.MALFORMED_AMOUNT),
                SaleCheck.check(sale("seven", "14000", "0", "14000", "14000", 0)).findings());
    }

    @Test
    void numberedWithoutANumberOrPendingWithOneIsInconsistent() {
        SaleBody numberedNoNumber = new SaleBody(SaleBody.V1, "KES",
                sale("100", "200", "0", "200", "200", 0).lines(), sale("100", "200", "0", "200", "200", 0).payments(),
                "200", new SaleBody.Fiscal(SaleBody.Fiscal.NUMBERED, ""), null);
        assertTrue(SaleCheck.check(numberedNoNumber).findings().contains(SaleCheck.Finding.FISCAL_INCONSISTENT));
    }

    @Test
    void v2WithoutACashierIsInconsistent() {
        SaleBody s = sale("100", "200", "0", "200", "200", 0);
        SaleBody v2 = new SaleBody(SaleBody.V2, s.currency(), s.lines(), s.payments(), s.totalMinor(), s.fiscal(), null);
        assertTrue(SaleCheck.check(v2).findings().contains(SaleCheck.Finding.VERSION_CASHIER_MISMATCH));
    }
}
