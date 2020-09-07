package com.github.zobkov.webhook

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
        List result = toList(event).collect { Object thing ->
            Map<String, ?> context = buildContext(exchange, event, thing)
            new MimeMessage((Session) null).tap {
                from = InternetAddress.parse(from(event, context)).first()
                subject = subject(event, context)
                text = body(event, context) + hookDebugInfo(event, context)
            }
        }
        return type.cast(result)
    }

    /**
     * Converts {@link GHEventPayload} event into a list.
     *
     * @param event One of {@link GHEventPayload} events.
     * @return a list that contains elements representing the event or the same event.
     */
    protected List<?> toList(E event) {
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

    /**
     * Returns webhook debug information that is appended to the email body.
     *
     * @param event One of {@link GHEventPayload} events.
     * @param context Translation context.
     * @return webhook debug information.
     */
    @SuppressWarnings('UnusedMethodParameter')
    protected String hookDebugInfo(E event, Map<String, ?> context) {
        StringBuilder builder = new StringBuilder()
        builder << '---\n'
        ['X-GitHub-Delivery'].each { String header ->
            builder << "${header}: ${context.get(header, 'is absent')}\n"
        }
        return builder.toString()
    }
}