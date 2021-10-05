package com.github.gee

import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GHRef
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GHTagObject
import org.kohsuke.github.GHUser
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitUser
import spock.lang.Specification

import javax.mail.Message
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class GHCreateToMimeMessageTest extends Specification {

    GHCreateToMimeMessage translator
    CamelContext context

    GitHub github = Mock()
    GHEventPayload.Create createEvent = Mock()
    GHRef ref = Mock()
    GHRef.GHObject refObject = Mock()
    GHTagObject tagObject = Mock()
    GHUser sender = Mock()
    GitUser tagger = Mock()
    GHRepository repository = Mock()

    def setup() {
        translator = new GHCreateToMimeMessage(gitHub: github)
        context = new DefaultCamelContext()
    }

    def "test evaluate"() {
        given:
        Exchange exchange = new DefaultExchange(context)
        exchange.in.body = createEvent
        when:
        List<MimeMessage> result = translator.evaluate(exchange, List<MimeMessage>)
        then:
        result.size() == 1
        with(MimeMessage.cast(result.first())){
            it.from == InternetAddress.parse('Monalisa Octocat <noreply@api.github.com>')
            it.subject == 'New tag in repository Codertocat/Hello-World - 1.0.0'
            it.getRecipients(Message.RecipientType.TO) == null
            it.contentType == 'text/plain'
            Object content = it.getContent()
            content != null
            with(String.cast(content).readLines()){
                it[0] == 'Annotated tag: 1.0.0'
                it[1] == 'Tagger: Monalisa Octocat / mona@github.com'
                it[2] == 'Message: Tag'
            }
        }

        github.apiUrl >> 'https://api.github.com'

        // https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#create
        repository.fullName >> 'Codertocat/Hello-World'

        ref.ref >> '1.0.0'
        refObject.sha >> 'abc'
        ref.object >> refObject
        repository.getRefs('tag') >> [ref].toArray()
        tagger.name >> 'Monalisa Octocat'
        tagger.email >> 'mona@github.com'
        tagObject.tagger >> tagger
        tagObject.message >> 'Tag'
        repository.getTagObject('abc') >> tagObject

        createEvent.repository >> repository
        sender.login >> 'Monalisa Octocat'
        createEvent.sender >> sender
        createEvent.refType >> 'tag'
        createEvent.ref >> '1.0.0'
    }

}
