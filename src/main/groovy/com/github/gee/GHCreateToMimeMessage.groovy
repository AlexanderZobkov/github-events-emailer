package com.github.gee

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GHRef
import org.kohsuke.github.GHTagObject
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitUser

/**
 * Translates {@link GHEventPayload.Create} into {@link javax.mail.internet.MimeMessage}.
 *
 * When tag is created:
 *   From: A user who created the tag
 *   Subject: New <refType> in repository <repository> - <ref>
 *   Body:
 *     Annotated/Lightweight tag: <tag name>
 *     Tagger: A user who created the tag
 *     Message: Tag message/commit message
 *
 * When branch is created:
 *   From: A user who created the tag
 *   Subject: New <refType> in repository <repository> - <ref>
 *   Body: A text telling that <refType> has been created in <repository> by <user>.
 */
@CompileStatic
@Slf4j
class GHCreateToMimeMessage extends AbstractGHEventToMimeMessage<GHEventPayload.Create> {

    private final Map<String, AbstractGHEventToMimeMessage> delegateSelector = [
            tag   : new TagCreated(),
            branch: new BranchCreated(),
    ]

    GitHub gitHub

    @Override
    protected String from(GHEventPayload.Create event, Map<String, ?> context) {
        delegateSelector[event.refType].from(event, context)
    }

    @Override
    protected String body(GHEventPayload.Create event, Map<String, ?> context) {
        delegateSelector[event.refType].body(event, context)
    }

    @Override
    protected String subject(GHEventPayload.Create event, Map<String, ?> context) {
        delegateSelector[event.refType].subject(event, context)
    }

    private String computeSubject(GHEventPayload.Create event) {
        return "New ${event.refType} in repository ${event.repository.fullName} - ${event.ref}"
    }

    private String computeFrom(GHEventPayload.Create event) {
        URL apiUrl = new URL(gitHub.apiUrl)
        return "${event.sender.login} <noreply@${apiUrl.host}>"
    }

    private class TagCreated extends AbstractGHEventToMimeMessage<GHEventPayload.Create> {

        @Override
        protected String from(GHEventPayload.Create event, Map<String, ?> context) {
            computeFrom(event)
        }

        @Override
        protected String body(GHEventPayload.Create event, Map<String, ?> context) {
            String ref = event.ref
            StringBuilder builder = new StringBuilder()
            GHRef tagRef = event.repository.getRefs('tag')
                    .find { GHRef tag ->
                        tag.ref - 'refs/heads/' - 'refs/tags/' == ref
                    }
            try {
                GHTagObject annotatedTag = event.repository.getTagObject(tagRef.object.sha)
                GitUser tagger = annotatedTag.tagger
                builder << "Annotated tag: ${ref}\n"
                builder << "Tagger: ${[tagger.name, tagger.email].join(' / ')}\n"
                builder << "Message: ${annotatedTag.message}\n"
            } catch (FileNotFoundException e) {
                GHCommit commit = event.repository.getCommit(tagRef.object.sha)
                GitUser author = commit.commitShortInfo.author
                builder << "Lightweight tag: ${ref}\n"
                builder << "Tagger: ${[author.name, author.email].join(' / ')}\n"
                builder << "Message: ${commit.commitShortInfo.message}\n"
            }
            return builder.toString()
        }

        @Override
        protected String subject(GHEventPayload.Create event, Map<String, ?> context) {
            computeSubject(event)
        }
    }

    private class BranchCreated extends AbstractGHEventToMimeMessage<GHEventPayload.Create> {

        @Override
        protected String from(GHEventPayload.Create event, Map<String, ?> context) {
            computeFrom(event)
        }

        @Override
        protected String body(GHEventPayload.Create event, Map<String, ?> context) {
            "The branch ${event.ref} has been created in repository ${event.repository.fullName} " +
                    "by ${event.sender.login}."
        }

        @Override
        protected String subject(GHEventPayload.Create event, Map<String, ?> context) {
            computeSubject(event)
        }
    }

}
