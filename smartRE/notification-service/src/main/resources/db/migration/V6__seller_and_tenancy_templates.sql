-- Notifications nobody was receiving.
--
-- Four events were being published and dropped on the floor by the router. Each is a
-- moment where somebody is waiting to be told something:
--
--   PAYMENT_COMPLETED  the buyer was told; the seller was not, so a seller could have
--                      their property paid for and find out by refreshing a page.
--   LEASE_ACTIVATED    a tenant's tenancy going live is the point at which rent starts
--                      and the dashboard becomes theirs.
--   LEASE_ENDED        the end of a tenancy has deposit and notice consequences.
--   VIEWING_COMPLETED  completing a viewing is what unlocks a buyer's right to review.
--
-- Two seller templates rather than one, because not every payment is a sale. A viewing
-- fee is money received; a deposit or full payment moves the property. Telling a seller
-- their house has sold when someone paid to view it would be a bad message to get wrong.

INSERT INTO notification_templates (code, channel, category, subject_template, body_template, html_body_template) VALUES

('SALE_PAYMENT_RECEIVED', 'EMAIL', 'PAYMENT',
 'Payment received for your property — KES [[${amount}]]',
 'Hello [[${fullName}]],

A buyer has paid for your property.

Amount:        KES [[${amount}]]
Type:          [[${paymentType}]]
M-Pesa receipt: [[${mpesaReceiptNumber}]]

The funds are held in escrow and released on completion.

See the payment: [[${receiptLink}]]

— SmartRE Kenya',
 '<p>Hello [[${fullName}]],</p>
<p>A buyer has <strong>paid for your property</strong>.</p>
<table class="facts" role="presentation">
  <tr><td class="k">Amount</td><td class="v">KES [[${amount}]]</td></tr>
  <tr><td class="k">Type</td><td class="v">[[${paymentType}]]</td></tr>
  <tr><td class="k">M-Pesa receipt</td><td class="v">[[${mpesaReceiptNumber}]]</td></tr>
</table>
<p>The funds are held in escrow and released on completion.</p>
<p><a class="btn" href="[[${receiptLink}]]">See the payment</a></p>'),

('SALE_PAYMENT_RECEIVED', 'IN_APP', 'PAYMENT',
 'Payment received for your property',
 'KES [[${amount}]] received ([[${paymentType}]]). Held in escrow until completion.', NULL),

('SELLER_PAYMENT_RECEIVED', 'EMAIL', 'PAYMENT',
 'You received a payment — KES [[${amount}]]',
 'Hello [[${fullName}]],

You have received a payment.

Amount:        KES [[${amount}]]
Type:          [[${paymentType}]]
M-Pesa receipt: [[${mpesaReceiptNumber}]]

See the payment: [[${receiptLink}]]

— SmartRE Kenya',
 '<p>Hello [[${fullName}]],</p>
<p>You have received a payment.</p>
<table class="facts" role="presentation">
  <tr><td class="k">Amount</td><td class="v">KES [[${amount}]]</td></tr>
  <tr><td class="k">Type</td><td class="v">[[${paymentType}]]</td></tr>
  <tr><td class="k">M-Pesa receipt</td><td class="v">[[${mpesaReceiptNumber}]]</td></tr>
</table>
<p><a class="btn" href="[[${receiptLink}]]">See the payment</a></p>'),

('SELLER_PAYMENT_RECEIVED', 'IN_APP', 'PAYMENT',
 'You received a payment',
 'KES [[${amount}]] received ([[${paymentType}]]).', NULL),

('LEASE_ACTIVATED', 'EMAIL', 'TENANCY',
 'Your tenancy at [[${unitLabel}]] is active',
 'Hello [[${fullName}]],

Your tenancy is now active.

Unit:       [[${unitLabel}]]
Rent:       KES [[${rentAmount}]]
Rent due:   day [[${billingDay}]] of each month
Started:    [[${startDate}]]

Invoices will appear in your dashboard a few days before each due date.

Your tenancy: [[${tenancyLink}]]

— SmartRE Kenya',
 '<p>Hello [[${fullName}]],</p>
<p>Your tenancy is now <strong>active</strong>.</p>
<table class="facts" role="presentation">
  <tr><td class="k">Unit</td><td class="v">[[${unitLabel}]]</td></tr>
  <tr><td class="k">Rent</td><td class="v">KES [[${rentAmount}]]</td></tr>
  <tr><td class="k">Rent due</td><td class="v">Day [[${billingDay}]] of each month</td></tr>
  <tr><td class="k">Started</td><td class="v">[[${startDate}]]</td></tr>
</table>
<p>Invoices appear in your dashboard a few days before each due date.</p>
<p><a class="btn" href="[[${tenancyLink}]]">Open your tenancy</a></p>'),

('LEASE_ACTIVATED', 'IN_APP', 'TENANCY',
 'Your tenancy is active',
 '[[${unitLabel}]] — KES [[${rentAmount}]], due day [[${billingDay}]] of each month.', NULL),

('LEASE_ENDED', 'EMAIL', 'TENANCY',
 'Your tenancy at [[${unitLabel}]] has ended',
 'Hello [[${fullName}]],

Your tenancy has been recorded as ended.

Unit:   [[${unitLabel}]]
Reason: [[${reason}]]

Any outstanding balance and deposit will be settled with your landlord.

Your tenancy: [[${tenancyLink}]]

— SmartRE Kenya',
 '<p>Hello [[${fullName}]],</p>
<p>Your tenancy has been recorded as <strong>ended</strong>.</p>
<table class="facts" role="presentation">
  <tr><td class="k">Unit</td><td class="v">[[${unitLabel}]]</td></tr>
  <tr><td class="k">Reason</td><td class="v">[[${reason}]]</td></tr>
</table>
<p>Any outstanding balance and deposit will be settled with your landlord.</p>
<p><a class="btn" href="[[${tenancyLink}]]">Open your tenancy</a></p>'),

('LEASE_ENDED', 'IN_APP', 'TENANCY',
 'Your tenancy has ended',
 '[[${unitLabel}]] — [[${reason}]]. Deposit and balance settle with your landlord.', NULL),

('VIEWING_COMPLETED', 'EMAIL', 'VIEWING',
 'How was the viewing?',
 'Hello [[${fullName}]],

Your viewing is marked complete, so you can now leave a review. Reviews are only
accepted from buyers who actually attended, which is what makes them worth reading.

Leave a review: [[${reviewLink}]]

— SmartRE Kenya',
 '<p>Hello [[${fullName}]],</p>
<p>Your viewing is marked complete, so you can now <strong>leave a review</strong>.</p>
<p>Reviews are only accepted from buyers who actually attended, which is what makes them
   worth reading.</p>
<p><a class="btn" href="[[${reviewLink}]]">Leave a review</a></p>'),

('VIEWING_COMPLETED', 'IN_APP', 'VIEWING',
 'You can review this property',
 'Your viewing is complete. Reviews are only accepted from buyers who attended.', NULL);
