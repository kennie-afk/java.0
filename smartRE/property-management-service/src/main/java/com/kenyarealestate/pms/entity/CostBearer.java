package com.kenyarealestate.pms.entity;

/**
 * Who pays for a repair.
 *
 * <p>Recorded at resolution rather than when the job is raised, because it usually is
 * not known until somebody has looked. A blocked drain is the landlord's problem until
 * the plumber pulls out a nappy.
 */
public enum CostBearer {

    /** Structural, fair wear and tear, or anything the landlord is liable for. */
    LANDLORD,

    /** Damage the tenant caused. Recharged in full. */
    TENANT,

    /**
     * Split between them, with the tenant's portion stated explicitly.
     *
     * <p>A shared job with no split recorded is an unfinished decision, which is why the
     * database refuses it.
     */
    SHARED
}
