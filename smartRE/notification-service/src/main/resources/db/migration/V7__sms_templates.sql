-- SMS variants for the notifications that are worth a text message.
--
-- Deliberately not all of them. SMS costs money per message and interrupts people, so it
-- is reserved for things that are time-sensitive or financial: money moving, rent falling
-- due or going late, and an urgent repair. A viewing invitation to leave a review does
-- not justify a text.
--
-- Every body below is written to fit inside 160 GSM-7 characters once the variables are
-- filled with realistic values, because Kenyan networks bill per 160-character part.
-- SmsChannel warns when a rendered message exceeds that, so a careless template edit
-- shows up in the logs rather than on the bill.
--
-- No subject and no html_body_template: neither means anything to a phone.

INSERT INTO notification_templates (code, channel, category, subject_template, body_template, html_body_template) VALUES

('RENT_DUE', 'SMS', 'TENANCY', NULL,
 'SmartRE: Rent for [[${unitLabel}]] of KES [[${amountDue}]] is due [[${dueDate}]]. Invoice [[${invoiceNumber}]]. Pay from your dashboard.', NULL),

('RENT_OVERDUE', 'SMS', 'TENANCY', NULL,
 'SmartRE: Rent for [[${unitLabel}]] is [[${daysOverdue}]] day(s) late. KES [[${balance}]] outstanding on [[${invoiceNumber}]]. Please settle it.', NULL),

('RENT_RECEIVED', 'SMS', 'TENANCY', NULL,
 'SmartRE: Payment of KES [[${amount}]] received for [[${unitLabel}]]. Balance KES [[${balance}]]. Invoice [[${invoiceNumber}]].', NULL),

('PAYMENT_COMPLETED', 'SMS', 'PAYMENT', NULL,
 'SmartRE: Payment of KES [[${amount}]] confirmed. M-Pesa ref [[${mpesaReceiptNumber}]]. Funds are held in escrow until completion.', NULL),

-- The one a seller most wants to hear immediately, and the reason SMS is wired at all.
('SALE_PAYMENT_RECEIVED', 'SMS', 'PAYMENT', NULL,
 'SmartRE: A buyer has paid KES [[${amount}]] ([[${paymentType}]]) for your property. M-Pesa ref [[${mpesaReceiptNumber}]]. Held in escrow.', NULL),

('SELLER_PAYMENT_RECEIVED', 'SMS', 'PAYMENT', NULL,
 'SmartRE: You received KES [[${amount}]] ([[${paymentType}]]). M-Pesa ref [[${mpesaReceiptNumber}]].', NULL),

('MAINTENANCE_RAISED', 'SMS', 'MAINTENANCE', NULL,
 'SmartRE: [[${priority}]] repair reported at [[${unitLabel}]] — [[${title}]]. Ref [[${reference}]]. Open it in your dashboard.', NULL),

('IDENTITY_APPROVED', 'SMS', 'VERIFICATION', NULL,
 'SmartRE: Your identity verification is approved. Your listings now carry the verified-seller badge.', NULL),

('OWNERSHIP_APPROVED', 'SMS', 'VERIFICATION', NULL,
 'SmartRE: Ownership of [[${propertyTitle}]] is verified against the Ministry of Lands registry. Your listing can go live.', NULL),

('LEASE_ACTIVATED', 'SMS', 'TENANCY', NULL,
 'SmartRE: Your tenancy at [[${unitLabel}]] is active. Rent KES [[${rentAmount}]], due day [[${billingDay}]] each month.', NULL);
