package com.github.gee

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.apache.camel.Processor
import org.apache.camel.builder.RouteBuilder
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GitHub
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * A route that translates events coming from github.
 */
@CompileStatic
@Component
class GHEventsTranslatorRoute extends RouteBuilder {

    @Autowired
    GitHub gitHub

    @Autowired
    @Qualifier(value = 'translator')
    Expression translator

    @Override
    void configure() throws Exception {
        from('seda:github-events').id('translator')
                .process(unmarshallEvent()).id('unmarshall-event')
                .transform(translator).id('translate-github-events')
                .to('seda:email-sender').id('to-email-sender')
    }

    Processor unmarshallEvent() {
        return new Processor() {
            void process(Exchange exchange) throws Exception {
                String eventType = exchange.in.headers['X-GitHub-Event']
                exchange.in.body = gitHub.parseEventPayload(
                        exchange.in.getMandatoryBody(Reader),
                        eventToClass(eventType))
            }
            private Class<GHEventPayload> eventToClass(String event) {
                return this.
                        class.
                        classLoader.
                        loadClass('org.kohsuke.github.GHEventPayload$' + event.capitalize())  as Class<GHEventPayload>
            }
        }
    }

}
