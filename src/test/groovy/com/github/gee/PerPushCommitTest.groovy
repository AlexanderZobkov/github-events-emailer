package com.github.gee

import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import org.kohsuke.github.GHCommit
import org.kohsuke.github.AbstractGHCommitRetriever
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GHRepository
import org.kohsuke.github.HttpException
import spock.lang.*

import javax.mail.Message
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.text.SimpleDateFormat
import java.util.regex.Matcher

class PerPushCommitTest extends Specification {

    PerPushCommit perPushCommit
    CamelContext context

    AbstractGHCommitRetriever diffRetriever = Mock()

    SimpleDateFormat format = new SimpleDateFormat('EEE MMM dd HH:mm:ss zzz yyyy')
    Date expectedCommitDate
    Date expectedAuthoredDate

    def setup() {
        perPushCommit = new PerPushCommit()
        perPushCommit.commitDiffRetriever = diffRetriever
        context = new DefaultCamelContext()
        expectedAuthoredDate = format.parse('Fri Oct 01 11:04:21 UTC 2021')
        expectedCommitDate = format.parse('Fri Oct 01 11:04:22 UTC 2021')
    }

    def "single commit"() {
        given:
        Exchange exchange = new DefaultExchange(context)
        exchange.in.body = createGHPushEvent([mockGHCommit()])
        when:
        List<MimeMessage> result = perPushCommit.evaluate(exchange, List<MimeMessage>)
        then:
        result.size() == 1
        with(MimeMessage.cast(result.first())) {
            it.from == InternetAddress.parse('21031067+Codertocat@users.noreply.github.com')
            it.subject == 'New commit in repository Codertocat/Hello-World/master - 6dcb09b Fix all the bugs'
            it.getRecipients(Message.RecipientType.TO) == null
            it.contentType == 'text/plain'
            Object content = it.getContent()
            content != null
            with(String.cast(content).readLines()) {
                it.size() == 13
                it[0] == 'URL: https://github.com/octocat/Hello-World/commit/6dcb09b5b57875f334f61aebed695e2e4193db5e'
                it[1] == 'SHA: 6dcb09b5b57875f334f61aebed695e2e4193db5e'
                it[2] == 'Author: Monalisa Octocat / mona@github.com'
                Matcher authorDateMatcher = it[3] =~ /AuthorDate: (.*)/
                authorDateMatcher.find()
                expectedAuthoredDate == format.parse(authorDateMatcher[0][1])
                it[4] == 'Commit: Leo Octocat / leo@github.com'
                Matcher commitDateMatcher = it[5] =~ /CommitDate: (.*)/
                commitDateMatcher.find()
                expectedCommitDate == format.parse(commitDateMatcher[0][1])
                it[6] == 'Parent commit(s): 6dcb09b5b57875f334f61aebed695e2e4193db5e'
                it[7] == 'Message: Fix all the bugs'
                it[8] == '---'
                it[9] == 'Files:'
                it[10] == 'Modified file1.txt'
                it[11] == '---'
                it[12] == 'diff'
            }
        }

        diffRetriever.getCommit(_, _) >> 'diff'
    }

    def "failed to get a diff for the commit"() {
        given:
        Exchange exchange = new DefaultExchange(context)
        exchange.in.body = createGHPushEvent([mockGHCommit()])
        when:
        List<MimeMessage> result = perPushCommit.evaluate(exchange, List<MimeMessage>)
        then:
        result.size() == 1
        with(MimeMessage.cast(result.first())) {
            it.from == InternetAddress.parse('21031067+Codertocat@users.noreply.github.com')
            it.subject == 'New commit in repository Codertocat/Hello-World/master - 6dcb09b Fix all the bugs'
            it.getRecipients(Message.RecipientType.TO) == null
            it.contentType == 'text/plain'
            Object content = it.getContent()
            content != null
            with(String.cast(content).readLines()) {
                it[11] == '---'
                it[12] == 'GitHub says: Server Error: Sorry, this diff is taking too long to generate.'
            }
        }

        diffRetriever.getCommit(_, _) >> {
            IOException e = new IOException('Server returned HTTP response code: 500 for URL: https://abc/02c4740274e045d2e87ff659c53abae5eeb875cd')
            String responseMessage = '''
{"message":"Server Error: Sorry, this diff is taking too long to generate.","errors":[{"resource":"Commit","field":"diff","code":"not_available"}],"documentation_url":"https://docs.github.com/enterprise/2.22/rest/reference/repos#get-a-commit"}
'''
            String errorMessage = 'Server Error: Sorry, this diff is taking too long to generate.'
            throw new HttpException(errorMessage, 500, responseMessage, 'https://abc/02c4740274e045d2e87ff659c53abae5eeb875cd', e);
        }
    }

    AbstractGHPushSplitter.ExpandedGHPushEvent createGHPushEvent(List<GHCommit> commits) {
        new AbstractGHPushSplitter.ExpandedGHPushEvent(event: mockGHEventPayloadPush(),
                commits: commits)
    }

    GHEventPayload.Push mockGHEventPayloadPush(String repositoryName = 'Codertocat/Hello-World',
                                               String pusherEmail = '21031067+Codertocat@users.noreply.github.com') {
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
                          Tuple3<String, String, Date> a = new Tuple3<>('Monalisa Octocat', 'mona@github.com', expectedAuthoredDate),
                          Tuple3<String, String, Date> c = new Tuple3<>('Leo Octocat', 'leo@github.com', expectedCommitDate)) {
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
        ghCommit.parentSHA1s >> ['6dcb09b5b57875f334f61aebed695e2e4193db5e',]
        GHCommit.File file1 = Mock()
        file1.fileName >> 'file1.txt'
        file1.status >> 'modified'
        ghCommit.files >> [file1,]
        ghCommit
    }
}
