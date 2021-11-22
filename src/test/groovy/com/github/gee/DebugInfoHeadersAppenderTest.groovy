package com.github.gee

import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import spock.lang.Specification

import javax.mail.Header
import javax.mail.internet.MimeMessage

class DebugInfoHeadersAppenderTest extends Specification {

    DebugInfoHeadersAppender appender
    CamelContext context

    def setup() {
        appender = new DebugInfoHeadersAppender(version: '1.2.3',
                name: 'name',
                commitId: '97a42c67')
        context = new DefaultCamelContext()
    }

    def "Populate GitHub and application specific headers"() {
        given:
        Exchange exchange = new DefaultExchange(context)
        Map<String, String> expectedHeaders = ['X-GitHub-Delivery'                     : '7fe7bfc8-484d-11ec-813a-218173548773',
                                               'X-GitHub-Enterprise-Host'              : 'github.corp.net',
                                               'X-GitHub-Enterprise-Version'           : '3.1.11',
                                               'X-GitHub-Event'                        : 'push',
                                               'X-GitHub-Hook-ID'                      : '8651137',
                                               'X-GitHub-Hook-Installation-Target-ID'  : '35955',
                                               'X-GitHub-Hook-Installation-Target-Type': 'organization',
                                               'X-Name-Version'                        : '1.2.3',
                                               'X-Name-CommitId'                       : '97a42c67']
        exchange.in.headers.putAll(expectedHeaders)
        exchange.in.body = [new MimeMessage(null).tap { it.text = 'Text' }]
        when:
        List<MimeMessage> result = appender.evaluate(exchange, List<MimeMessage>)
        MimeMessage message = MimeMessage.cast(result.first())
        List<Header> headers = message.allHeaders.toList()
        Map<String, String> actualHeaders = headers.collectEntries({ header -> [header.name, header.value] })
        then:
        result.size() == 1
        actualHeaders == expectedHeaders
    }

}
