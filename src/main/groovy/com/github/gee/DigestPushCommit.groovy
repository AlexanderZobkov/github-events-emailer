package com.github.gee

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.xml.MarkupBuilder
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHEventPayload

import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Translates {@link AbstractGHPushSplitter.ExpandedGHPushEvent} into {@link MimeMessage}.
 *
 * Produces output similar to output of the command: git log --pretty=format:'%h -%d %s (%cr) <%an>/<%cn>'
 */
@CompileStatic
class DigestPushCommit implements Expression {

    @Override
    <T> T evaluate(Exchange exchange, Class<T> type) {
        AbstractGHPushSplitter.ExpandedGHPushEvent expandedGHPushEvent =
                exchange.in.getMandatoryBody(AbstractGHPushSplitter.ExpandedGHPushEvent)
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
    @SuppressWarnings('ExplicitCallToDivMethod')
    // div is html element here
    @CompileStatic(TypeCheckingMode.SKIP)
    private String body(GHEventPayload.Push event, List<GHCommit> commits) {
        StringWriter reply = new StringWriter()
        MarkupBuilder builder = new MarkupBuilder(reply)
        builder.html {
            builder.body {
                commits.reverse().each { ghCommit ->
                    String sha = ghCommit.SHA1
                    GHCommit.ShortInfo info = ghCommit.commitShortInfo
                    Date commitDate = ghCommit.commitDate
                    long ago = Duration.between(commitDate.toInstant(), ZonedDateTime.now()).toDays()
                    builder.div {
                        builder.a(href: "${ghCommit.htmlUrl}", "${sha.take(7)}")
                        mkp.yield " - ${info.message.readLines().first()}"
                        builder.b("(${ago} day(s) ago)")
                        mkp.yield "<${info.author.name}>/<${info.committer.name}>"
                    }
                }
            }
        }
        return reply.toString()
    }

    @SuppressWarnings('UnusedPrivateMethodParameter')
    private Map<String, String> headers(GHEventPayload.Push event, List<GHCommit> commits) {
        return ['Content-Type': 'text/html',]
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
