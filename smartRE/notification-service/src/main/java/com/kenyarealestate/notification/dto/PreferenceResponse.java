package com.kenyarealestate.notification.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PreferenceResponse {
    private String category;
    private boolean emailEnabled;
    private boolean smsEnabled;
    private boolean inAppEnabled;

    /**
     * Whether this category has any template written for the channel at all.
     *
     * <p>A toggle for a channel that has nothing to send is a promise the system cannot
     * keep, and the preferences screen previously offered exactly that for SMS across
     * every category. SMS is deliberately reserved for time-sensitive and financial
     * updates — texts cost money and interrupt people — so most categories have no SMS
     * variant, and the UI needs to be able to say so rather than showing a switch that
     * does nothing.
     */
    private boolean emailAvailable;
    private boolean smsAvailable;
    private boolean inAppAvailable;
}
