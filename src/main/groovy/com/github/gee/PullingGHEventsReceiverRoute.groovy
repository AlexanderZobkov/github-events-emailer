package com.github.gee

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.ExchangePattern
import org.apache.camel.builder.endpoint.EndpointRouteBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * A route that pulls events from Github.
 * https://docs.github.com/en/rest/reference/activity#events
 */
@ConditionalOnProperty(
        value='method.retrieve.events',
        havingValue = 'poller',
        matchIfMissing = false)
@CompileStatic
@Component
@SuppressWarnings('DuplicateStringLiteral')
class PullingGHEventsReceiverRoute extends EndpointRouteBuilder {

    /**
     * App config.
     */
    @Autowired
    AppConfig appConfig

    /**
     * A delay between attempts to retrieve events from GitHub.
     * In milliseconds.
     */
    @Value('${poller.delay}')
    long delay

    @Override
    void configure() throws Exception {
        from(scheduler('scheduler').initialDelay(0).delay(delay)
                .useFixedDelay(false).greedy(true))
                .routeId('pull-events')
                .setProperty(Exchange.SCHEDULER_POLLED_MESSAGES, constant(false)).id('reset-scheduler-polled-messages')
                .transform().exchange {
                    appConfig.ghEventsPoller().receive()
                }.id('pull-events')
                .process().exchange {
                    it.properties[Exchange.SCHEDULER_POLLED_MESSAGES] =
                            (it.in.body as boolean)
                }.id('set-scheduler-polled-messages')
                .transform(
                        new GHEventsInfoToGHEventPayloadTranslator(github: appConfig.github())
                ).id('translate-github-eventinfos')
                .to(ExchangePattern.InOnly,
                        seda('github-events').blockWhenFull(true)).id('to-github-events')
    }

}
