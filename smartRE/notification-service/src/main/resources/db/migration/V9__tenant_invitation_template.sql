-- The tenant invitation email.
--
-- Addressed to someone who does not yet have an account, which is the whole point: the
-- token in the link is what proves they own the address the landlord recorded, and
-- redeeming it is what makes the tenancy something the tenant accepted rather than
-- something asserted about them.
--
-- Sent with the landlord as userId and the tenant's address as recipientEmail. The
-- dispatcher treats recipientEmail as an override and skips the contact lookup entirely,
-- so delivery reaches the tenant while the record sits in the history of the person who
-- actually performed the action.
INSERT INTO notification_templates (code, channel, category, subject_template, body_template, html_body_template) VALUES

('TENANT_INVITATION', 'EMAIL', 'TENANCY',
 '[[${landlordName}]] has invited you to your tenancy on SmartRE',
 'Hello,

[[${landlordName}]] has set up your tenancy for [[${unitLabel}]] on SmartRE and invited you to see it.

Open this link to create your account and connect to the tenancy:
[[${actionUrl}]]

You will be able to see your lease, your rent invoices and their receipts, and raise maintenance requests.

This link is for you alone and expires in [[${expiresInHours}]] hours. If you were not expecting it, ignore this message - nothing is connected to you until the link is opened.',
 '<p>Hello,</p>
  <p><strong>[[${landlordName}]]</strong> has set up your tenancy for <strong>[[${unitLabel}]]</strong> on SmartRE and invited you to see it.</p>
  <p><a class="btn" href="[[${actionUrl}]]">Create your account</a></p>
  <p>You will be able to see your lease, your rent invoices and their receipts, and raise maintenance requests.</p>
  <p class="muted">This link is for you alone and expires in [[${expiresInHours}]] hours. If you were not expecting it, ignore this message &mdash; nothing is connected to you until the link is opened.</p>');
