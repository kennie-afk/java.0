-- HTML bodies for the nine email templates.
--
-- Two columns rather than one, because every email now goes out multipart: the existing
-- body_template stays as the text/plain alternative and html_body_template becomes the
-- text/html one. That is not belt-and-braces — a mail client that cannot or will not
-- render HTML still gets a readable message, and spam filters treat an HTML-only mail
-- with more suspicion than a multipart one.
--
-- What is stored here is a FRAGMENT, not a whole document. The header carrying the logo,
-- the styles and the footer live in EmailHtmlShell on the Java side. The reason is
-- practical: a template stored per-row is edited by a migration, and nobody should need
-- a database migration to change the brand colour or fix a rendering bug in Outlook.
-- Content belongs in the row; presentation belongs in code.
--
-- The markup is deliberately plain — no flexbox, no grid, no external CSS. Email clients
-- are roughly fifteen years behind browsers, and Outlook still renders through Word.
-- Tables and inline styles are what survive.

ALTER TABLE notification_templates
    ADD COLUMN IF NOT EXISTS html_body_template TEXT;

-- Rent
UPDATE notification_templates SET html_body_template =
'<p>Hello [[${fullName}]],</p>
<p>Rent for <strong>[[${unitLabel}]]</strong> is due on <strong>[[${dueDate}]]</strong>.</p>
<table class="facts" role="presentation">
  <tr><td class="k">Invoice</td><td class="v">[[${invoiceNumber}]]</td></tr>
  <tr><td class="k">Amount</td><td class="v">KES [[${amountDue}]]</td></tr>
</table>
<p><a class="btn" href="[[${invoiceLink}]]">Pay this invoice</a></p>'
WHERE code = 'RENT_DUE' AND channel = 'EMAIL';

UPDATE notification_templates SET html_body_template =
'<p>Hello [[${fullName}]],</p>
<p>Rent for <strong>[[${unitLabel}]]</strong> was due on [[${dueDate}]] and is now
   <strong>[[${daysOverdue}]] day(s) late</strong>.</p>
<table class="facts" role="presentation">
  <tr><td class="k">Invoice</td><td class="v">[[${invoiceNumber}]]</td></tr>
  <tr><td class="k">Outstanding</td><td class="v alert">KES [[${balance}]]</td></tr>
</table>
<p><a class="btn" href="[[${invoiceLink}]]">Settle it now</a></p>'
WHERE code = 'RENT_OVERDUE' AND channel = 'EMAIL';

UPDATE notification_templates SET html_body_template =
'<p>Hello [[${fullName}]],</p>
<p>We have recorded your rent payment.</p>
<table class="facts" role="presentation">
  <tr><td class="k">Unit</td><td class="v">[[${unitLabel}]]</td></tr>
  <tr><td class="k">Invoice</td><td class="v">[[${invoiceNumber}]]</td></tr>
  <tr><td class="k">Paid</td><td class="v">KES [[${amount}]]</td></tr>
  <tr><td class="k">Outstanding</td><td class="v">KES [[${balance}]]</td></tr>
</table>
<p><a class="btn" href="[[${invoiceLink}]]">View the invoice</a></p>'
WHERE code = 'RENT_RECEIVED' AND channel = 'EMAIL';

-- Maintenance
UPDATE notification_templates SET html_body_template =
'<p>Hello [[${fullName}]],</p>
<p>A maintenance request has been raised for <strong>[[${unitLabel}]]</strong>.</p>
<table class="facts" role="presentation">
  <tr><td class="k">Reference</td><td class="v">[[${reference}]]</td></tr>
  <tr><td class="k">Category</td><td class="v">[[${category}]]</td></tr>
  <tr><td class="k">Priority</td><td class="v">[[${priority}]]</td></tr>
  <tr><td class="k">Reported</td><td class="v">[[${title}]]</td></tr>
</table>
<p><a class="btn" href="[[${requestLink}]]">Open the request</a></p>'
WHERE code = 'MAINTENANCE_RAISED' AND channel = 'EMAIL';

UPDATE notification_templates SET html_body_template =
'<p>Hello [[${fullName}]],</p>
<p>The maintenance request for <strong>[[${unitLabel}]]</strong> has been marked
   <strong>[[${status}]]</strong>.</p>
<table class="facts" role="presentation">
  <tr><td class="k">Reference</td><td class="v">[[${reference}]]</td></tr>
  <tr><td class="k">Job</td><td class="v">[[${title}]]</td></tr>
</table>
<p class="quote">[[${resolutionNotes}]]</p>
<p><a class="btn" href="[[${requestLink}]]">See what was done</a></p>'
WHERE code = 'MAINTENANCE_RESOLVED' AND channel = 'EMAIL';

-- Verification
UPDATE notification_templates SET html_body_template =
'<p>Hello [[${fullName}]],</p>
<p>Your identity verification has been <strong>approved</strong>. Your listings now carry
   the verified-seller badge, which buyers filter for.</p>
<table class="facts" role="presentation">
  <tr><td class="k">Valid until</td><td class="v">[[${expiresAt}]]</td></tr>
</table>
<p><a class="btn" href="[[${dashboardLink}]]">Go to your dashboard</a></p>'
WHERE code = 'IDENTITY_APPROVED' AND channel = 'EMAIL';

UPDATE notification_templates SET html_body_template =
'<p>Hello [[${fullName}]],</p>
<p>Ownership of <strong>[[${propertyTitle}]]</strong> has been verified against the
   Ministry of Lands registry. The listing can now go live.</p>
<table class="facts" role="presentation">
  <tr><td class="k">Parcel</td><td class="v">[[${parcelNumber}]]</td></tr>
</table>
<p><a class="btn" href="[[${propertyLink}]]">View the listing</a></p>'
WHERE code = 'OWNERSHIP_APPROVED' AND channel = 'EMAIL';

-- Payment
UPDATE notification_templates SET html_body_template =
'<p>Hello [[${fullName}]],</p>
<p>Your payment has been received.</p>
<table class="facts" role="presentation">
  <tr><td class="k">Amount</td><td class="v">KES [[${amount}]]</td></tr>
  <tr><td class="k">M-Pesa receipt</td><td class="v">[[${mpesaReceipt}]]</td></tr>
</table>
<p><a class="btn" href="[[${paymentLink}]]">View the payment</a></p>'
WHERE code = 'PAYMENT_COMPLETED' AND channel = 'EMAIL';

-- Account
--
-- No button and no branding flourish on this one on purpose. A password-reset mail is the
-- single most impersonated message a product sends, so it stays visually plain and the
-- link is shown in full rather than hidden behind friendly text — a reader can then see
-- where it actually goes before clicking.
UPDATE notification_templates SET html_body_template =
'<p>Hello [[${fullName}]],</p>
<p>Someone asked to reset the password on this account. If that was not you, ignore this
   message and nothing will change.</p>
<p>Reset link, valid for a short time:</p>
<p class="plainlink">[[${resetLink}]]</p>'
WHERE code = 'PASSWORD_RESET' AND channel = 'EMAIL';

-- The rendered HTML is stored alongside the rendered text on the notification itself.
-- A retry must resend exactly what was composed the first time: re-rendering later would
-- silently pick up an edited template or changed data, so the second attempt would not be
-- the message the first attempt tried to deliver.
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS html_body TEXT;
