INSERT INTO notification_templates (code, channel, category, subject_template, body_template) VALUES

('PASSWORD_RESET', 'EMAIL', 'ACCOUNT',
 'Reset your SmartRE password',
 'Hello [[${fullName}]],

We received a request to reset your SmartRE password.

Reset it here: [[${resetLink}]]

This link expires in [[${expiryMinutes}]] minutes. If you did not ask for this, you can ignore this email — your password will not change.

— SmartRE Kenya'),

('IDENTITY_APPROVED', 'EMAIL', 'VERIFICATION',
 'Your SmartRE identity is verified',
 'Hello [[${fullName}]],

Your identity verification has been approved. Your listings now carry the verified-seller badge, which buyers filter for.

Your verification is valid until [[${expiresAt}]].

View your dashboard: [[${dashboardLink}]]

— SmartRE Kenya'),

('IDENTITY_APPROVED', 'IN_APP', 'VERIFICATION',
 'Identity verified',
 'Your identity verification was approved. Your listings now show the verified-seller badge.'),

('OWNERSHIP_APPROVED', 'EMAIL', 'VERIFICATION',
 'Property ownership verified',
 'Hello [[${fullName}]],

Ownership of your property has been verified against title deed [[${titleDeedNumber}]] (parcel [[${parcelNumber}]]).

The listing is now fully trusted and ranks above unverified listings in search.

View the listing: [[${propertyLink}]]

— SmartRE Kenya'),

('OWNERSHIP_APPROVED', 'IN_APP', 'VERIFICATION',
 'Ownership verified',
 'Ownership of your property was verified against title deed [[${titleDeedNumber}]]. The listing is now fully trusted.'),

('PAYMENT_COMPLETED', 'EMAIL', 'PAYMENT',
 'Payment received — [[${currency}]] [[${amount}]]',
 'Hello [[${fullName}]],

We have received your payment.

Amount:      [[${currency}]] [[${amount}]]
Type:        [[${paymentType}]]
M-Pesa code: [[${mpesaReceiptNumber}]]

View your receipt: [[${receiptLink}]]

— SmartRE Kenya'),

('PAYMENT_COMPLETED', 'IN_APP', 'PAYMENT',
 'Payment received',
 'We received [[${currency}]] [[${amount}]] — M-Pesa code [[${mpesaReceiptNumber}]].');
