package com.kenyarealestate.property.entity;

public enum ListingStatus {
    DRAFT,
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    SOLD,
    RENTED,
    WITHDRAWN,

    /**
     * Exists to be managed, never to be advertised.
     *
     * <p>A landlord with a block of eight flats needs somewhere for those units, leases
     * and tenants to hang off. They do not necessarily want the building on a public
     * marketplace, and requiring them to create a listing — with a price, a photo and a
     * verification queue — to reach the management tools is asking them to advertise a
     * property that is not for sale.
     *
     * <p>Every public query in this service filters on ACTIVE, so an UNLISTED property is
     * invisible by construction rather than by remembering to exclude it. It can be
     * published later, at which point it enters the ordinary verification path like any
     * other listing.
     */
    UNLISTED
}
