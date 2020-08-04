package com.github.zobkov.webhook

import groovy.transform.CompileStatic
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GHRef
import org.kohsuke.github.GHTagObject
import org.kohsuke.github.GitUser

/**
 * Translates {@link GHEventPayload.Create} into {@link javax.mail.internet.MimeMessage}.
 *
 * Supports:
 * - tags
*/
@CompileStatic
@SuppressWarnings('DuplicateStringLiteral')
class GHCreateToMimeMessage extends AbstractGHEventToMimeMessage<GHEventPayload.Create> {

    @Override
    protected String from(GHEventPayload.Create event, Map<String, ?> context) {
        checkRefType(event)
        GHRef tagRef = event.repository.getRefs('tag')
                .find { GHRef tag -> tag.ref - 'refs/heads/' - 'refs/tags/' == event.ref }
        try {
            GHTagObject annotatedTag = event.repository.getTagObject(tagRef.object.sha)
            return annotatedTag.tagger.email
        } catch (FileNotFoundException e) {
            GHCommit commit = event.repository.getCommit(tagRef.object.sha)
            return commit.commitShortInfo.author.email
        }
    }

    @Override
    protected String body(GHEventPayload.Create event, Map<String, ?> context) {
        checkRefType(event)

        String ref = event.ref
        StringBuilder builder = new StringBuilder()
        GHRef tagRef = event.repository.getRefs('tag')
                .find { GHRef tag -> tag.ref - 'refs/heads/' - 'refs/tags/' == ref }
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

        builder << '---\n'
        ['X-GitHub-Delivery'].each { String header ->
            builder << "${header}: ${context.get(header, 'is absent')} + \n"
        }

        return builder.toString()
    }

    @Override
    protected String subject(GHEventPayload.Create event, Map<String, ?> context) {
        return "New ${event.refType} in repository ${event.repository.fullName} - ${event.ref}"
    }

    private void checkRefType(GHEventPayload.Create event) {
        String refType = event.refType
        if (refType != 'tag') {
            throw new IOException("Not supported ref type: ${refType}")
        }
    }
}
