package com.github.gee

import groovy.transform.CompileStatic
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GitHub

/**
 * Translates {@link org.kohsuke.github.GHEventPayload.Delete} into {@link javax.mail.internet.MimeMessage}.
 *
 * From: A user who deleted the tag/branch
 * Subject: Deleted <refType> from <repository>
 * Body: A text telling that <refType> has been deleted from <repository> by <user>.
 */
@CompileStatic
class GHDeleteToMimeMessage extends AbstractGHEventToMimeMessage<GHEventPayload.Delete> {

    GitHub gitHub

    @Override
    protected String from(GHEventPayload.Delete event, Map<String, ?> context) throws IOException {
        URL apiUrl = new URL(gitHub.apiUrl)
        "${event.sender.login} <noreply@${apiUrl.host}>"
    }

    @Override
    protected String body(GHEventPayload.Delete event, Map<String, ?> context) throws IOException {
        "The ${event.refType} ${event.ref} has been deleted from repository ${event.repository.fullName} " +
                "by ${event.sender.login}."
    }

    @Override
    protected String subject(GHEventPayload.Delete event, Map<String, ?> context) throws IOException {
        "${event.refType.capitalize()} has been deleted from repository ${event.repository.fullName} - ${event.ref}"
    }

}
