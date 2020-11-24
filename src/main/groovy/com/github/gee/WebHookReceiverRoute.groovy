package com.github.gee

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.ExchangePattern
import org.apache.camel.Expression
import org.apache.camel.LoggingLevel
import org.apache.camel.Processor
import org.apache.camel.builder.RouteBuilder
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GitHub
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * A route that listens for calls from Github hooks.
 */
@CompileStatic
@Component
class WebHookReceiverRoute extends RouteBuilder {

    @Autowired
    GitHub gitHub

    @Value('${webhook.listen.address}')
    String webhookListenAddress

    @Value('${webhook.listen.port}')
    int webhookListenPort

    @SuppressWarnings('GStringExpressionWithinString')
    @Override
    void configure() throws Exception {
        from("jetty:http://${webhookListenAddress}:${webhookListenPort}?matchOnUriPrefix=true")
                .routeId('github-webhook')
                .process(unmarshallEvent()).id('unmarshall-event')
                .choice().id('events-filter')
                    .when().expression(shouldPassEvent())
                        .to(ExchangePattern.InOnly, 'seda:github-events').id('to-github-events')
                    .otherwise()
                        .log(LoggingLevel.WARN, 'Ignoring event: ${header[X-GitHub-Event]}').id('log-ignored-event')
                .end()
        .removeHeaders('*').id('remove-headers-replying-webhook')
        .setBody().constant('').id('set-webhook-reply')
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

    Expression shouldPassEvent() {
        return new Expression() {
            def <T> T evaluate(Exchange exchange, Class<T> type) {
                return type.cast([GHEventPayload.Push,
                           GHEventPayload.Create,
                           GHEventPayload.Delete].contains(exchange.in.body.class))
            }
        }
    }
}
