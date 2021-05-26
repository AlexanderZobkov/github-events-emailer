package com.github.gee

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j2
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GitHub

/**
 * Translates {@link org.kohsuke.github.GHEventPayload.Fork} into {@link javax.mail.internet.MimeMessage}.
 *
 * From: A user who created the fork
 * Subject: New fork for <repository name>
 * Body: A text telling that a particular user forked <the repository> into private or public <repository>
 *     and <repository url at github>.
 */
@CompileStatic
@SuppressWarnings('DuplicateStringLiteral')
@Log4j2
class GHForkToMimeMessage extends AbstractGHEventToMimeMessage<GHEventPayload.Fork> {

    GitHub gitHub

    @Override
    protected String from(GHEventPayload.Fork event, Map<String, ?> context) throws IOException {
        URL apiUrl = new URL(gitHub.apiUrl)
        "${event.sender.login} <noreply@${apiUrl.host}>"
    }

    @Override
    protected String body(GHEventPayload.Fork event, Map<String, ?> context) throws IOException {
        "A user: ${event.sender.login} forked the repository: ${event.repository.fullName} " +
                "into a ${event.forkee.private ? 'private' : 'public'} repository: ${event.forkee.fullName} " +
                "(${event.forkee.htmlUrl})"
    }

    @Override
    protected String subject(GHEventPayload.Fork event, Map<String, ?> context) throws IOException {
        "New fork for ${event.repository.fullName}"
    }
}
