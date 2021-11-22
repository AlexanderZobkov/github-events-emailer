package com.github.gee

import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import spock.lang.Specification

import javax.mail.internet.MimeMessage

class DebugInfoBodyAppenderTest extends Specification {

    DebugInfoBodyAppender appender
    CamelContext context

    def setup() {
        appender = new DebugInfoBodyAppender()
        context = new DefaultCamelContext()
    }

    def "Append to plain text email with X-GitHub headers"() {
        given:
        Exchange exchange = new DefaultExchange(context)
        exchange.in.body = [new MimeMessage(null).tap {it.text = 'Text' }]
        exchange.in.headers['X-Header'] = 'value'
        exchange.in.headers['X-GitHub-Delivery'] = '7fe7bfc8-484d-11ec-813a-218173548773'
        when:
        List<MimeMessage> result = appender.evaluate(exchange, List<MimeMessage>)
        then:
        MimeMessage message = MimeMessage.cast(result.first())
        String body = message.content as String
        with(body.readLines()){
            it.size() == 3
            it[0] == 'Text'
            it[1] == '---'
            it[2] == 'X-GitHub-Delivery: 7fe7bfc8-484d-11ec-813a-218173548773'
        }
    }

    def "Append to plain text email with no X-GitHub headers"() {
        given:
        Exchange exchange = new DefaultExchange(context)
        exchange.in.body = [new MimeMessage(null).tap {it.text = 'Text' }]
        exchange.in.headers['X-Header'] = 'value'
        when:
        List<MimeMessage> result = appender.evaluate(exchange, List<MimeMessage>)
        then:
        result.size() == 1
        MimeMessage message = MimeMessage.cast(result.first())
        String body = message.content as String
        with(body.readLines()){
            it.size() == 1
            it[0] == 'Text'
        }
    }
}
