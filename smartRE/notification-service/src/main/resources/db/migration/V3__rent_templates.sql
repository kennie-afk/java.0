INSERT INTO notification_templates (code, channel, category, subject_template, body_template) VALUES

('RENT_DUE', 'EMAIL', 'TENANCY',
 'Rent due [[${dueDate}]] — [[${unitLabel}]]',
 'Hello [[${fullName}]],

Rent for [[${unitLabel}]] is due on [[${dueDate}]].

Invoice: [[${invoiceNumber}]]
Amount:  KES [[${amountDue}]]

Pay from your dashboard: [[${invoiceLink}]]

— SmartRE Kenya'),

('RENT_DUE', 'IN_APP', 'TENANCY',
 'Rent due [[${dueDate}]]',
 'KES [[${amountDue}]] for [[${unitLabel}]] is due on [[${dueDate}]]. Invoice [[${invoiceNumber}]].'),

('RENT_OVERDUE', 'EMAIL', 'TENANCY',
 'Rent overdue — [[${unitLabel}]]',
 'Hello [[${fullName}]],

Rent for [[${unitLabel}]] was due on [[${dueDate}]] and is now [[${daysOverdue}]] day(s) late.

Invoice:     [[${invoiceNumber}]]
Outstanding: KES [[${balance}]]

Settle it here: [[${invoiceLink}]]

If you have already paid, ignore this — it can take a few minutes to show.

— SmartRE Kenya'),

('RENT_OVERDUE', 'IN_APP', 'TENANCY',
 'Rent overdue',
 'KES [[${balance}]] for [[${unitLabel}]] is [[${daysOverdue}]] day(s) overdue. Invoice [[${invoiceNumber}]].'),

('RENT_RECEIVED', 'EMAIL', 'TENANCY',
 'Rent received — [[${unitLabel}]]',
 'Hello [[${fullName}]],

We have recorded your rent payment.

Unit:        [[${unitLabel}]]
Invoice:     [[${invoiceNumber}]]
Paid:        KES [[${amount}]]
Outstanding: KES [[${balance}]]

View it here: [[${invoiceLink}]]

— SmartRE Kenya'),

('RENT_RECEIVED', 'IN_APP', 'TENANCY',
 'Rent received',
 'KES [[${amount}]] recorded against invoice [[${invoiceNumber}]] for [[${unitLabel}]].');
