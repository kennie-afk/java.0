-- The PROPERTY category had no templates on any channel.
--
-- That was visible in the preferences screen as a "Listings" row with three live
-- switches, none of which controlled anything: a seller could enable listing emails and
-- receive nothing, forever. The cause was upstream — property-service published no events
-- at all — and this fills the gap now that it does.
--
-- Suspension is the one listing change worth interrupting somebody for. A seller whose
-- property has stopped appearing in search needs to know that it has, and why; every
-- other listing transition is either something they did themselves or is already covered
-- by the verification notifications.
--
-- SMS is included. This is exactly the "urgent" case the channel is reserved for: the
-- seller's livelihood is off the market and the clock is running on fixing it.

INSERT INTO notification_templates (code, channel, category, subject_template, body_template, html_body_template) VALUES

('LISTINGS_SUSPENDED', 'EMAIL', 'PROPERTY',
 'Your listings have been suspended',
 'Hello [[${fullName}]],

[[${suspendedCount}]] of your listing(s) have been suspended and are no longer visible to
buyers.

Reason: [[${reason}]]

If you believe this is a mistake, reply to this message or contact support. Your listings
and their history are kept — nothing has been deleted.

Your listings: [[${listingsLink}]]

— SmartRE Kenya',
 '<p>Hello [[${fullName}]],</p>
<p><strong>[[${suspendedCount}]]</strong> of your listing(s) have been suspended and are no
   longer visible to buyers.</p>
<table class="facts" role="presentation">
  <tr><td class="k">Reason</td><td class="v alert">[[${reason}]]</td></tr>
</table>
<p>If you believe this is a mistake, contact support. Your listings and their history are
   kept — nothing has been deleted.</p>
<p><a class="btn" href="[[${listingsLink}]]">Open your listings</a></p>'),

('LISTINGS_SUSPENDED', 'IN_APP', 'PROPERTY',
 'Your listings have been suspended',
 '[[${suspendedCount}]] listing(s) are no longer visible to buyers. Reason: [[${reason}]]', NULL),

('LISTINGS_SUSPENDED', 'SMS', 'PROPERTY', NULL,
 'SmartRE: [[${suspendedCount}]] of your listing(s) have been suspended and are no longer visible to buyers. Reason: [[${reason}]]', NULL);
