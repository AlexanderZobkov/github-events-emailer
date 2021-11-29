package com.github.gee

import groovy.xml.XmlParser
import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GHRepository
import spock.lang.Specification

import javax.mail.Message
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class DigestPushCommitTest extends Specification {

    DigestPushCommit digestPushCommit
    CamelContext context

    Clock clock = Clock.fixed(Instant.parse("2021-11-01T11:04:21Z"), ZoneId.of("UTC"));

    def setup() {
        digestPushCommit = new DigestPushCommit(referencedClock: clock)
        context = new DefaultCamelContext()
    }

    def "single commit"() {
        given:
        Exchange exchange = new DefaultExchange(context)
        exchange.in.body = createGHPushEvent([mockGHCommit()])
        when:
        List<MimeMessage> result = digestPushCommit.evaluate(exchange, List<MimeMessage>)
        then:
        result.size() == 1
        with(MimeMessage.cast(result.first())) {
            it.from == InternetAddress.parse('21031067+Codertocat@users.noreply.github.com')
            it.subject == 'New commits in repository Codertocat/Hello-World/master'
            it.getRecipients(Message.RecipientType.TO) == null
            it.contentType == 'text/html'
            Object content = it.getContent()
            content != null
            with(new XmlParser().parseText(String.cast(content))) {
                NodeList divs = it.'body'.'div'
                divs.size() == 1
                with(Node.cast(divs[0]).children()) { List divChildren ->
                    Node a = Node.cast(divChildren[0])
                    a.name() == 'a'
                    a.attributes().size() == 1
                    a.attributes()['href'] == 'https://github.com/octocat/Hello-World/commit/6dcb09b5b57875f334f61aebed695e2e4193db5e'
                    a.value()[0] == '6dcb09b'
                    divChildren[1] == ' - Fix all the bugs\n' + '      '
                    Node b = Node.cast(divChildren[2])
                    b.name() == 'b'
                    b.attributes().size() == 0
                    b.value()[0] == '(31 day(s) ago)'
                    divChildren[3] == '<Monalisa Octocat>/<Leo Octocat>\n' + '    '
                }
            }
        }
    }

    def "multiple commits"() {
        given:
        Exchange exchange = new DefaultExchange(context)
        List<GHCommit> commits = (1..10).collect { mockGHCommit("${it}") }
        exchange.in.body = createGHPushEvent(commits)
        when:
        List<MimeMessage> result = digestPushCommit.evaluate(exchange, List<MimeMessage>)
        then:
        result.size() == 1
        with(MimeMessage.cast(result.first())) {
            it.from == InternetAddress.parse('21031067+Codertocat@users.noreply.github.com')
            it.subject == 'New commits in repository Codertocat/Hello-World/master'
            it.getRecipients(Message.RecipientType.TO) == null
            it.contentType == 'text/html'
            Object content = it.getContent()
            content != null
            Node html = new XmlParser().parseText(String.cast(content))
            List<Integer> actualShaList = html.'body'.'div'.'a'.collect { Node node -> Integer.valueOf(node.value()[0]) }
            (1..10).toList().reverse() == actualShaList
        }
    }

    AbstractGHPushSplitter.ExpandedGHPushEvent createGHPushEvent(List<GHCommit> commits) {
        new AbstractGHPushSplitter.ExpandedGHPushEvent(event: mockGHEventPayloadPush(),
                commits: commits)
    }

    GHEventPayload.Push mockGHEventPayloadPush(String repositoryName = 'Codertocat/Hello-World',
                                               String pusherEmail='21031067+Codertocat@users.noreply.github.com') {
        GHEventPayload.Push pushEvent = Mock()
        GHEventPayload.Push.Pusher pusher = Mock()
        GHRepository repository = Mock()
        // https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#push
        repository.fullName >> repositoryName
        pusher.email >> pusherEmail
        pushEvent.repository >> repository
        pushEvent.pusher >> pusher
        pushEvent.ref >> 'refs/heads/master'
        pushEvent
    }

    GHCommit mockGHCommit(String sha = '6dcb09b5b57875f334f61aebed695e2e4193db5e',
                          String message = 'Fix all the bugs',
                          Tuple3<String, String, Date> a = new Tuple3<>('Monalisa Octocat', 'mona@github.com', Date.from(Instant.parse("2021-10-01T11:04:21Z"))),
                          Tuple3<String, String, Date> c = new Tuple3<>('Leo Octocat', 'leo@github.com', Date.from(Instant.parse("2021-10-01T11:04:22Z")))) {
        GHCommit ghCommit = Mock()
        GHCommit.ShortInfo shortInfo = Mock()
        GHCommit.GHAuthor author = Mock()
        GHCommit.GHAuthor commiter = Mock()
        // https://docs.github.com/en/rest/reference/repos#get-a-commit
        ghCommit.SHA1 >> sha
        ghCommit.htmlUrl >> new URL("https://github.com/octocat/Hello-World/commit/${sha}")
        ghCommit.commitDate >> a.v3
        shortInfo.message >> message
        author.name >> a.v1
        author.email >> a.v2
        shortInfo.author >> author
        shortInfo.authoredDate >> a.v3
        commiter.name >> c.v1
        commiter.email >> c.v2
        shortInfo.committer >> commiter
        shortInfo.commitDate >> c.v3
        ghCommit.commitShortInfo >> shortInfo
        ghCommit
    }

}
