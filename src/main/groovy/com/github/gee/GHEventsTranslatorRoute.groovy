package com.github.gee

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.apache.camel.builder.endpoint.EndpointRouteBuilder
import org.kohsuke.github.GHEventPayload
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * A route that translates events coming from github.
 */
@CompileStatic
@Component
class GHEventsTranslatorRoute extends EndpointRouteBuilder {

    /**
     * App config.
     */
    @Autowired
    AppConfig appConfig

    @Override
    void configure() throws Exception {
        from(seda('github-events').concurrentConsumers(1)).id('translator')
                .transform(unmarshallEvent()).id('unmarshall-event')
                .transform(delegatingTranslator()).id('translate-github-events')
                .to(seda('email-sender').blockWhenFull(true)).id('to-email-sender')
    }

    Expression unmarshallEvent() {
        new Expression() {
            @Override
            <T> T evaluate(Exchange exchange, Class<T> type) {
                String eventType = exchange.in.headers['X-GitHub-Event']
                appConfig.github().parseEventPayload(
                        exchange.in.getMandatoryBody(Reader),
                        eventToClass(eventType)) as T
            }

            private Class<GHEventPayload> eventToClass(String event) {
                return this.
                        class.
                        classLoader.
                        loadClass('org.kohsuke.github.GHEventPayload$' + event.capitalize()) as Class<GHEventPayload>
            }
        }
    }

    Expression delegatingTranslator() {
        new Expression() {
            @Override
            <T> T evaluate(Exchange exchange, Class<T> type) {
                GHEventPayload payload = exchange.in.getMandatoryBody(GHEventPayload)
                Expression delegate = appConfig.translationMap()[payload.class as Class]
                return delegate.evaluate(exchange, type)
            }
        }
    }

}
