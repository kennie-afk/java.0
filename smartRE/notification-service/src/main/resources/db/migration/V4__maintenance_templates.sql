INSERT INTO notification_templates (code, channel, category, subject_template, body_template) VALUES

('MAINTENANCE_RAISED', 'EMAIL', 'MAINTENANCE',
 '[[${priority}]] repair request — [[${unitLabel}]]',
 'Hello [[${fullName}]],

A repair has been reported on [[${unitLabel}]].

Reference: [[${reference}]]
Category:  [[${category}]]
Priority:  [[${priority}]]
Reported:  [[${title}]]

Open it here: [[${requestLink}]]

— SmartRE Kenya'),

('MAINTENANCE_RAISED', 'IN_APP', 'MAINTENANCE',
 'Repair reported on [[${unitLabel}]]',
 '[[${priority}]] — [[${title}]]. Reference [[${reference}]].'),

('MAINTENANCE_RESOLVED', 'EMAIL', 'MAINTENANCE',
 'Repair [[${status}]] — [[${unitLabel}]]',
 'Hello [[${fullName}]],

Your repair request for [[${unitLabel}]] is now [[${status}]].

Reference: [[${reference}]]
Reported:  [[${title}]]
Notes:     [[${resolutionNotes}]]

View it here: [[${requestLink}]]

— SmartRE Kenya'),

('MAINTENANCE_RESOLVED', 'IN_APP', 'MAINTENANCE',
 'Repair [[${status}]]',
 '[[${title}]] on [[${unitLabel}]] is now [[${status}]]. Reference [[${reference}]].');
