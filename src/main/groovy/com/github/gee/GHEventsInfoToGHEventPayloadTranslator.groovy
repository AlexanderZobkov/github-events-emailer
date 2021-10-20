package com.github.gee

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j2
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.kohsuke.github.GHEvent
import org.kohsuke.github.GHEventInfo
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub

import java.lang.reflect.Field

/**
 * Translates {@link GHEventInfo} into {@link GHEventPayload}.
 */
@Log4j2
@CompileStatic
class GHEventsInfoToGHEventPayloadTranslator implements Expression {

    GitHub github

    @Override
    <T> T evaluate(Exchange exchange, Class<T> type) {
        List<GHEventInfo> eventInfos = exchange.message.getMandatoryBody(List<GHEventInfo>)
        List<GHEventPayload> answer = eventInfos.collect { GHEventInfo eventInfo ->
            GHEvent eventType = eventInfo.getType()
            if (eventType == GHEvent.PUSH) {
                GHEventPayload.Push push = eventInfo.getPayload(GHEventPayload.Push)
                enrichGHEventPayload(eventInfo, push)
                exchange.message.headers['X-GitHub-Event'] = 'push'
                GHEventPayload.cast(push)
            } else {
                log.warn("Ignoring event: ${eventType}, " +
                        "accepting: ${GHEvent.PUSH}")
                null
            }
        }.grep()
        type.cast(answer)
    }

    /**
     *  Events in Events API and events sent to webhooks are not the same.
     *  However events in Events API include a field payload that contains events as sent by webhooks
     *  but these events miss certain fields that are required for composing emails.
     *
     *  This method enrich event with the following fields:
     *   - repository
     *   - pusher
     */
    private void enrichGHEventPayload(GHEventInfo eventInfo, GHEventPayload.Push event) {
        // Inject pusher field through magic
        Field pusherField = GHEventPayload.Push.
                getDeclaredField('pusher')
        pusherField.setAccessible(true)
        GHEventPayload.Push.Pusher pusher = new GHEventPayload.Push.Pusher()
        Field emailField = GHEventPayload.Push.Pusher.getDeclaredField('email')
        emailField.setAccessible(true)
        String login = eventInfo.actor.login
        URL apiUrl = new URL(github.apiUrl)
        emailField.set(pusher, "${login} <noreply@${apiUrl.host}>".toString())
        pusherField.set(event, pusher)
        // Inject repository field through magic
        Field repositoryField = GHEventPayload.
                getDeclaredField('repository')
        repositoryField.setAccessible(true)
        GHRepository repository = eventInfo.repository
        repositoryField.set(event, repository)
    }
}
