package com.github.gee

import groovy.transform.CompileStatic
import org.apache.camel.ExchangePattern
import org.apache.camel.Message
import org.apache.camel.builder.RouteBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * A route that listens for calls from Github webhooks.
 */
@CompileStatic
@Component
@SuppressWarnings('DuplicateStringLiteral')
class WebHookReceiverRoute extends RouteBuilder {

    @Value('${webhook.listen.address}')
    String webhookListenAddress

    @Value('${webhook.listen.port}')
    int webhookListenPort

    @Value('${webhook.events.accept}')
    List<String> eventsToAccept

    @Override
    void configure() throws Exception {
        from("jetty:http://${webhookListenAddress}:${webhookListenPort}?matchOnUriPrefix=true")
                .routeId('github-webhook')
                .choice().id('events-filter')
                    .when().message(shouldPass)
                        .to(ExchangePattern.InOnly, 'seda:github-events').id('to-github-events')
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
}
