package com.github.gee

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j2
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
@Log4j2('routeLog')
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
                GHEventPayload unmarshalledEvent = appConfig.github().parseEventPayload(
                        exchange.in.getMandatoryBody(Reader),
                        eventToClass(eventType))
                type.cast(unmarshalledEvent)
            }

            private Class<GHEventPayload> eventToClass(String eventType) {
                return this.
                        class.
                        classLoader.
                        loadClass('org.kohsuke.github.GHEventPayload$' +
                                eventType.capitalize()) as Class<GHEventPayload>
            }
        }
    }

    Expression delegatingTranslator() {
        new Expression() {
            private final String supportedEvents = appConfig.translationMap().keySet().join(',')

            @Override
            <T> T evaluate(Exchange exchange, Class<T> type) {
                String eventType = exchange.in.headers['X-GitHub-Event']
                Expression translator = appConfig.translationMap()[eventType]
                if (translator) {
                    return translator.evaluate(exchange, type)
                }
                routeLog.warn("Ignoring unsupported event: ${eventType}, " +
                        "supported events: ${supportedEvents}")
                return type.cast([])
            }
        }
    }

}
