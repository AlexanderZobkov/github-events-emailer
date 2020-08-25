package com.github.zobkov.webhook

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHCommitDiffRetriever
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GitUser
import org.springframework.beans.factory.annotation.Autowired

/**
 * Translates {@link GHEventPayload.Push} into {@link javax.mail.internet.MimeMessage}.
 */
@CompileStatic
class GHPushToMimeMessage extends AbstractGHEventToMimeMessage<GHEventPayload.Push> {

    @Autowired
    GHCommitDiffRetriever commitRetriever

    @Override
    protected Map<String, ?> buildContext(Exchange exchange, GHEventPayload.Push event, Object thing) {
        return super.buildContext(exchange, event, thing) + [commit: thing]
    }

    @Override
    protected String from(GHEventPayload.Push event, Map<String, ?> context) {
        return event.pusher.email
    }

    @SuppressWarnings('DuplicateStringLiteral')
    @Override
    protected String body(GHEventPayload.Push event, Map<String, ?> context) {
        GHEventPayload.Push.PushCommit commit = context['commit'] as GHEventPayload.Push.PushCommit

        StringBuilder builder = new StringBuilder()

        String sha = commit.sha

        builder << "URL: ${commit.url}\n"
        builder << "SHA: ${sha}\n"

        GHCommit ghCommit = event.repository
                .getCommit(sha)

        GHCommit.ShortInfo shortInfo = ghCommit.commitShortInfo
        GitUser author = shortInfo.author
        builder << "Author: ${[author.name, author.email].join(' / ')}\n"
        builder << "AuthorDate: ${shortInfo.authoredDate}\n"
        GitUser committer = shortInfo.committer
        builder << "Commit: ${[committer.name, committer.email].join(' / ')}\n"
        builder << "CommitDate: ${shortInfo.commitDate}\n"

        builder << "Parent commits: ${ghCommit.parentSHA1s.join(', ')}\n"

        builder << "Message: ${shortInfo.message}\n"

        builder << '---\n'
        builder << 'Files:\n'
        builder << event.repository
                .getCommit(sha)
                .files
                .collect { GHCommit.File file -> "${file.status.capitalize()} ${file.fileName}" }
                .join('\n') + '\n'

        builder << '---\n'
        builder << commitRetriever.getCommit(event.repository, sha) + '\n'

        return builder.toString()
    }

    @SuppressWarnings('DuplicateStringLiteral')
    @Override
    protected String subject(GHEventPayload.Push event, Map<String, ?> context) {
        GHEventPayload.Push.PushCommit commit = context['commit'] as GHEventPayload.Push.PushCommit
        return 'New commit in repository ' +
                "${event.repository.fullName}/${event.ref - 'refs/heads/' - 'refs/tags/'} " +
                "- ${commit.sha.take(7)} ${commit.message.readLines().first().take(50)}"
    }

    @Override
    protected List<?> toList(GHEventPayload.Push event) {
        return event.commits
    }
}
