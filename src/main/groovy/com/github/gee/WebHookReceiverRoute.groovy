package com.github.gee

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.ExchangePattern
import org.apache.camel.Expression
import org.apache.camel.Message
import org.apache.camel.builder.endpoint.EndpointRouteBuilder
import org.kohsuke.github.GHEventPayload
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * A route that listens for calls from Github webhooks.
 */
@CompileStatic
@Component
@SuppressWarnings('DuplicateStringLiteral')
class WebHookReceiverRoute extends EndpointRouteBuilder {

    /**
     * App config.
     */
    @Autowired
    AppConfig appConfig

    @Value('${webhook.listen.address}')
    String webhookListenAddress

    @Value('${webhook.listen.port}')
    int webhookListenPort

    @Value('${webhook.events.accept}')
    List<String> eventsToAccept

    @Override
    void configure() throws Exception {
        from(jetty("http://${webhookListenAddress}:${webhookListenPort}").matchOnUriPrefix(true))
                .routeId('github-webhook')
                .choice().id('events-filter')
                    .when().message(shouldPass)
                        .transform(unmarshallEvent()).id('unmarshall-event')
                        .to(ExchangePattern.InOnly,
                                seda('github-events').blockWhenFull(true)).id('to-github-events')
                    .otherwise()
                        .process().message(logIgnoredEvent).id('log-ignored-event')
                .end()
        .removeHeaders('*').id('remove-headers-replying-webhook')
        .setBody().constant('').id('set-webhook-reply')
    }

    Closure<Boolean> shouldPass = { Message message ->
        eventsToAccept.contains('*') ?: String.cast(message.headers['X-GitHub-Event']) in eventsToAccept
    }

    Closure<Void> logIgnoredEvent = { Message message ->
        log.warn("Ignoring event: ${message.headers['X-GitHub-Event']}, " +
                "accepting: ${eventsToAccept.join(', ')}")
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

}
