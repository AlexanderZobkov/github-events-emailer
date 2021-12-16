package com.github.gee

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.kohsuke.github.GHCommit
import org.kohsuke.github.AbstractGHCommitRetriever
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GitUser

import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Translates {@link AbstractGHPushSplitter.ExpandedGHPushEvent} into {@link MimeMessage}.
 */
@CompileStatic
@Slf4j
class PerPushCommit implements Expression {

    AbstractGHCommitRetriever commitDiffRetriever

    @Override
    <T> T evaluate(Exchange exchange, Class<T> type) {
        AbstractGHPushSplitter.ExpandedGHPushEvent expandedPushEvent =
                exchange.in.getMandatoryBody(AbstractGHPushSplitter.ExpandedGHPushEvent)
        GHEventPayload.Push event = expandedPushEvent.event
        List<GHCommit> commits = expandedPushEvent.commits

        List<MimeMessage> answer = commits.collect { GHCommit commit ->
            buildMimeMessage(from(event, commit),
                    subject(event, commit),
                    [:],
                    body(event, commit))
        }
        return type.cast(answer)
    }

    @SuppressWarnings('UnusedPrivateMethodParameter')
    private String from(GHEventPayload.Push event, GHCommit commit) {
        return event.pusher.email
    }

    private String subject(GHEventPayload.Push event, GHCommit commit) {
        GHCommit.ShortInfo info = commit.commitShortInfo
        String commitMessage = info.message?.readLines()?.first()
        return 'New commit in repository ' +
                "${event.repository.fullName}/${event.ref - 'refs/heads/' - 'refs/tags/'} " +
                "- ${commit.SHA1.take(7)} ${commitMessage ?: 'No commit message'}"
    }

    @SuppressWarnings('DuplicateStringLiteral')
    private String body(GHEventPayload.Push event, GHCommit ghCommit) {
        StringBuilder builder = new StringBuilder()

        builder << "URL: ${ghCommit.htmlUrl}\n"
        builder << "SHA: ${ghCommit.SHA1}\n"

        GHCommit.ShortInfo shortInfo = ghCommit.commitShortInfo
        GitUser author = shortInfo.author
        builder << "Author: ${[author.name, author.email].join(' / ')}\n"
        builder << "AuthorDate: ${shortInfo.authoredDate}\n"
        GitUser committer = shortInfo.committer
        builder << "Commit: ${[committer.name, committer.email].join(' / ')}\n"
        builder << "CommitDate: ${shortInfo.commitDate}\n"

        builder << "Parent commit(s): ${ghCommit.parentSHA1s.join(', ')}\n"

        builder << "Message: ${shortInfo.message ?: 'No commit message'}\n"

        builder << '---\n'
        if (ghCommit.files) {
            builder << 'Files:\n'
            builder << ghCommit.files
                    .collect { GHCommit.File file -> "${file.status.capitalize()} ${file.fileName}" }
                    .join('\n') + '\n'
            builder << '---\n'
            try {
                // Need to get a diff as GHCommit.File.patch contains only textual diffs
                // however there is a need to get textual and binary diffs.
                builder << commitDiffRetriever.getCommit(event.repository, ghCommit.SHA1)
            } catch (IOException e) {
                log.error('Can\'t get the diff from GitHub for the commit {} in the repository {}/{}',
                        ghCommit.SHA1,
                        event.repository.ownerName,
                        event.repository.name,
                        e)
                builder << 'GitHub says: ' + e.message
            }
        } else {
            builder << 'Files: No changes\n'
        }
        return builder.toString()
    }

    private MimeMessage buildMimeMessage(String from, String subject, Map<String, String> headers, String body) {
        return new MimeMessage((Session) null).tap {
            it.from = InternetAddress.parse(from).first()
            it.subject = subject
            setText(body, null)
            headers.each { String header, String value -> setHeader(header, value) }
        }
    }
}
