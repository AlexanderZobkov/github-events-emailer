package com.github.zobkov.webhook

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHCommitDiffRetriever
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GHEventPayload.Push.PushCommit
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitUser

import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Translates {@link GHEventPayload.Push} into {@link javax.mail.internet.MimeMessage}.
 */
@CompileStatic
class GHPushToMimeMessage implements Expression {

    private final PerPushCommit perCommitMimeMessages = new PerPushCommit()
    private final DigestPushCommit digestMimeMessage = new DigestPushCommit()

    GHCommitDiffRetriever commitDiffRetriever

    int maxCommitAge

    @Override
    <T> T evaluate(Exchange exchange, Class<T> type) {
        GHEventPayload.Push event = exchange.in.getMandatoryBody(GHEventPayload.Push)
        List<GHCommit> ghCommits = convertToGHCommits(event.repository, event.commits)
        List<List<GHCommit>> newAndOld = splitCommits(ghCommits)
        List<MimeMessage> perCommitEmails =
                perCommitMimeMessages.evaluate(buildNewExcange(exchange, event, newAndOld[0]), List<MimeMessage>)
        List<MimeMessage> digestEmail =
                digestMimeMessage.evaluate(buildNewExcange(exchange, event, newAndOld[1]), List<MimeMessage>)
        return type.cast(perCommitEmails + digestEmail)
    }

    private Exchange buildNewExcange(Exchange exchange, GHEventPayload.Push event, List<GHCommit> commmits) {
        return exchange.copy().tap {
            message.body = new ExpandedGHPushEvent(
                            event  : event,
                            commits: commmits)
        }
    }

    private List<GHCommit> convertToGHCommits(GHRepository repository, List<PushCommit> pushCommits) {
        return pushCommits.collect { PushCommit commit ->
            repository.getCommit(commit.sha)
        }
    }

    private List<List<GHCommit>> splitCommits(List<GHCommit> pushCommits) {
        return pushCommits.split { GHCommit commit ->
            long commitAge = Duration.between(commit.commitDate.toInstant(), ZonedDateTime.now()).toDays()
            commitAge < maxCommitAge
        }
    }

    private class PerPushCommit implements Expression {

        @Override
        <T> T evaluate(Exchange exchange, Class<T> type) {
            ExpandedGHPushEvent map = exchange.in.getMandatoryBody(ExpandedGHPushEvent)
            GHEventPayload.Push event = map.event
            List<GHCommit> commits = map.commits

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
            return 'New commit in repository ' +
                    "${event.repository.fullName}/${event.ref - 'refs/heads/' - 'refs/tags/'} " +
                    "- ${commit.SHA1.take(7)} ${info.message.readLines().first()}"
        }

        @SuppressWarnings('DuplicateStringLiteral')
        private String body(GHEventPayload.Push event, GHCommit ghCommit) {
            StringBuilder builder = new StringBuilder()

            String sha = ghCommit.SHA1

            builder << "URL: ${ghCommit.htmlUrl}\n"
            builder << "SHA: ${sha}\n"

            GHCommit.ShortInfo shortInfo = ghCommit.commitShortInfo
            GitUser author = shortInfo.author
            builder << "Author: ${[author.name, author.email].join(' / ')}\n"
            builder << "AuthorDate: ${shortInfo.authoredDate}\n"
            GitUser committer = shortInfo.committer
            builder << "Commit: ${[committer.name, committer.email].join(' / ')}\n"
            builder << "CommitDate: ${shortInfo.commitDate}\n"

            builder << "Parent commit(s): ${ghCommit.parentSHA1s.join(', ')}\n"

            builder << "Message: ${shortInfo.message}\n"

            builder << '---\n'
            builder << 'Files:\n'
            builder << event.repository
                    .getCommit(sha)
                    .files
                    .collect { GHCommit.File file -> "${file.status.capitalize()} ${file.fileName}" }
                    .join('\n') + '\n'

            builder << '---\n'
            builder << commitDiffRetriever.getCommit(event.repository, sha) + '\n'

            return builder.toString()
        }
    }

    private class DigestPushCommit implements Expression {

        @Override
        <T> T evaluate(Exchange exchange, Class<T> type) {
            ExpandedGHPushEvent expandedGHPushEvent = exchange.in.getMandatoryBody(ExpandedGHPushEvent)
            GHEventPayload.Push event = expandedGHPushEvent.event
            List<GHCommit> commits = expandedGHPushEvent.commits
            return commits.empty ?
                    type.cast([]) :
                    type.cast([
                            buildMimeMessage(from(event, commits),
                                    subject(event, commits),
                                    headers(event, commits),
                                    body(event, commits))
                    ])
        }

        @SuppressWarnings('UnusedPrivateMethodParameter')
        private String from(GHEventPayload.Push event, List<GHCommit> commits) {
            return event.pusher.email
        }

        @SuppressWarnings('UnusedPrivateMethodParameter')
        private String subject(GHEventPayload.Push event, List<GHCommit> commits) {
            return 'New commits in repository ' +
                    "${event.repository.fullName}/${event.ref - 'refs/heads/' - 'refs/tags/'} "
        }

        @SuppressWarnings('UnusedPrivateMethodParameter')
        private String body(GHEventPayload.Push event, List<GHCommit> commits) {
            StringBuilder builder = new StringBuilder()
            builder << '''\
<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml">
<body>
'''
            commits.reverse().each { ghCommit ->
                String sha = ghCommit.SHA1
                GHCommit.ShortInfo info = ghCommit.commitShortInfo
                Date commitDate = ghCommit.commitDate
                long ago = Duration.between(commitDate.toInstant(), ZonedDateTime.now()).toDays()
                builder << "<a href='${ghCommit.htmlUrl}'>${sha.take(7)}</a> - ${info.message.readLines().first()} "
                builder << "<b>(${ago} day(s) ago)</b> &lt;${info.committer.name}&gt;"
                builder << '<br/>'
            }

            builder << '''\
</body>
</html>
'''
            return builder.toString()
        }

        @SuppressWarnings('UnusedPrivateMethodParameter')
        private Map<String, String> headers(GHEventPayload.Push event, List<GHCommit> commits) {
            return ['Content-Type': 'text/html',]
        }

    }

    private class ExpandedGHPushEvent {
        GHEventPayload.Push event
        List<GHCommit> commits
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
