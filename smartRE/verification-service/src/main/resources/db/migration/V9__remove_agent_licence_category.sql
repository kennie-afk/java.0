-- The agent role was removed (user-service V11), so the estate-agent licence category
-- has nothing left to serve: nobody can hold the role that required it.
--
-- Any documents already filed under it are deleted rather than reassigned. Reassigning
-- would guess at what the uploader meant, and a licence certificate is not a substitute
-- for any of the remaining identity categories. Deleting is the honest outcome for
-- evidence supporting a discontinued application.
--
-- The column is a VARCHAR with no CHECK constraint, so this cannot fail on a database
-- that never held one.

DELETE FROM seller_identity_documents
 WHERE document_category = 'AGENT_LICENSE_ESTATE_AGENTS_BOARD';
