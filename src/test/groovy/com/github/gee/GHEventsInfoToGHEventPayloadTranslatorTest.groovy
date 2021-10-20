package com.github.gee

import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import org.kohsuke.github.GHEvent
import org.kohsuke.github.GHEventInfo
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GHUser
import org.kohsuke.github.GitHub
import spock.lang.Specification

import java.time.Instant

class GHEventsInfoToGHEventPayloadTranslatorTest extends Specification {

    GHEventsInfoToGHEventPayloadTranslator translator
    CamelContext context

    GitHub github = Mock()
    GHUser ghUser = Mock()
    GHRepository ghRepository = Mock()

    def setup() {
        translator = new GHEventsInfoToGHEventPayloadTranslator(github: github)
        context = new DefaultCamelContext()
    }

    def "push"() {
        given:
        Exchange exchange = new DefaultExchange(context)
        GHEventInfo eventInfo = Mock()
        exchange.in.body = [eventInfo]
        when:
        List<GHEventPayload> result = translator.evaluate(exchange, List<GHEventPayload>)
        then:
        result.size() == 1
        with(GHEventPayload.cast(result.first())){
            it.repository //.fullName == 'x'
            it.pusher.email == 'mega men <noreply@api.github.com>'
        }

        github.apiUrl >> 'https://api.github.com'

        ghUser.login >> 'mega men'
        github.apiUrl >> 'https://github.api/v3'

        ghRepository.fullName >> 'mega-man-repo'

        eventInfo.getCreatedAt() >> Date.from(Instant.now())
        eventInfo.getType() >> GHEvent.PUSH
        eventInfo.getPayload(_) >> new GHEventPayload.Push()
        eventInfo.getActor() >> ghUser
        eventInfo.getRepository() >> ghRepository
    }

    def "non push"() {
        given:
        Exchange exchange = new DefaultExchange(context)
        GHEventInfo eventInfo = Mock()
        exchange.in.body = [eventInfo]
        when:
        List<GHEventPayload> result = translator.evaluate(exchange, List<GHEventPayload>)
        then:
        result.size() == 0

        eventInfo.getCreatedAt() >> Date.from(Instant.now())
        eventInfo.getType() >> GHEvent.PULL_REQUEST
        eventInfo.getPayload(_) >> new GHEventPayload.PullRequest()
        eventInfo.getActor() >> ghUser
        eventInfo.getRepository() >> ghRepository
    }
}
