package com.github.gee

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.kohsuke.github.GHEventPayload

import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Abstract translator with base methods to translate {@link GHEventPayload} events to {@link MimeMessage}
 *
 * @param < E >   Specific type of {@link GHEventPayload} event.
 */
@CompileStatic
abstract class AbstractGHEventToMimeMessage<E extends GHEventPayload> implements Expression {

    @Override
    <T> T evaluate(Exchange exchange, Class<T> type) {
        GHEventPayload event = exchange.in.getMandatoryBody(GHEventPayload)
        List<MimeMessage> result = toList(exchange, event).collect { Object thing ->
            Map<String, ?> context = buildContext(exchange, event, thing)
            new MimeMessage((Session) null).tap {
                from = InternetAddress.parse(from(event, context)).first()
                subject = subject(event, context)
                setText(body(event, context), null)
            }
        }
        return type.cast(result)
    }

    /**
     * Converts {@link GHEventPayload} event into a list.
     *
     * @param exchange the exchange.
     * @param event One of {@link GHEventPayload} events.
     * @return a list that contains elements representing the event or the same event.
     */
    @SuppressWarnings('UnusedMethodParameter')
    protected List<?> toList(Exchange exchange, E event) {
        return [event]
    }

    /**
     * Builds translation context.
     *
     * @param exchange the exchange.
     * @param event One of {@link GHEventPayload} events.
     * @param thing an element from the list returned by {@link #toList(org.kohsuke.github.GHEventPayload)}.
     * @return Translation context.
     */
    @SuppressWarnings('UnusedMethodParameter')
    protected Map<String, ?> buildContext(Exchange exchange, E event, Object thing) {
        return [:].tap { putAll(exchange.in.headers) }
    }

    /**
     * Translates 'From' field.
     *
     * @param event One of {@link GHEventPayload} events.
     * @param context Translation context.
     * @return the field value.
     * @throws IOException If error occurred while translating the field.
     */
    protected abstract String from(E event, Map<String, ?> context) throws IOException

    /**
     * Translates email body.
     *
     * @param event One of {@link GHEventPayload} events.
     * @param context Translation context.
     * @return the field value.
     * @throws IOException  If error occurred while translating the field.
     */
    protected abstract String body(E event, Map<String, ?> context) throws IOException

    /**
     * Translates 'Subject' field.
     *
     * @param event One of {@link GHEventPayload} events.
     * @param context Translation context.
     * @return the field value.
     * @throws IOException  If error occurred while translating the field.
     */
    protected abstract String subject(E event, Map<String, ?> context) throws IOException

}
