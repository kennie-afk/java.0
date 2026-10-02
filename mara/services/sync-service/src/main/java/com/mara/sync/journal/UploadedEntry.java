package com.mara.sync.journal;

import com.mara.platform.sale.SaleBody;

/** One journal record as the terminal stores and uploads it. Digests and signature are lower-case hex. */
public record UploadedEntry(
        long sequence,
        String terminalId,
        long epochSecond,
        int nano,
        SaleBody sale,
        String bodyDigest,
        String previousDigest,
        String digest,
        String signature) {
}
