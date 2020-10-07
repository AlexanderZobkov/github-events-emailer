package com.github.zobkov.webhook

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.xml.XmlParser
import groovy.xml.XmlUtil
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.springframework.lang.NonNull

import javax.mail.internet.MimeMessage

/**
 * Appends webhook debug information to the email.
 *
 * Adds X-GitHub-xxx headers from HTTP request to SMTP headers.
 * Adds X-GitHub-Delivery header to the email body. It supports plain text and html emails.
 */
@CompileStatic
class DebugInfoAppender implements Expression {

    @NonNull
    Expression delegate

    @Override
    <T> T evaluate(Exchange exchange, Class<T> type) {
        List<MimeMessage> result = delegate.evaluate(exchange, List<MimeMessage>)
        List<MimeMessage> answer = result.collect { MimeMessage mimeMessage ->
            populateHeaders(mimeMessage, exchange)
            appendTail(mimeMessage, exchange)
            mimeMessage
        }
        return type.cast(answer)
    }

    protected void populateHeaders(MimeMessage message, Exchange exchange) {
        exchange.message.headers.each { String header, Object value ->
            if (header.startsWithIgnoreCase('X-GitHub')) {
                message.setHeader(header, value.toString())
            }
        }
    }

    protected String calculateTailText(Exchange exchange) {
        StringBuilder builder = new StringBuilder()
        builder << '---\n'
        ['X-GitHub-Delivery'].each { String header ->
            builder << "${header}: ${exchange.message.headers.get(header, 'is absent')}\n"
        }
        return builder.toString()
    }

    private void appendTail(MimeMessage message, Exchange exchange) {
        String tail = calculateTailText(exchange)
        AppenderFactory.create(message).appendText(tail)
    }

    private interface Appender {
        void appendText(String textToAppend)
    }

    private class AppenderFactory {

        static Appender create(MimeMessage message) {
            if (message.isMimeType('text/html')) {
                return new HtmlMessage(null, message)
            } else if (message.isMimeType('text/plain')) {
                return new PlainTextMessage(null, message)
            }
            throw new IOException("Unsupported mime type: ${message.contentType}")
        }

        private class PlainTextMessage implements Appender {

            private final String body
            private final MimeMessage message

            PlainTextMessage(MimeMessage message) {
                body = message.content
                this.message = message
            }

            void appendText(String textToAppend) {
                message.text = [body, textToAppend].join('\n')
            }
        }

        private class HtmlMessage implements Appender {

            private final Node html

            private final MimeMessage message

            HtmlMessage(MimeMessage message) {
                String body = message.content
                html = new XmlParser().parseText(body)
                this.message = message
            }

            @CompileStatic(TypeCheckingMode.SKIP)
            void appendText(String textToAppend) {
                Node htmlBody = html.body[0]
                textToAppend.readLines().each { String line ->
                    htmlBody.children().with {
                        add(line)
                        add(new Node(null, 'br'))
                    }
                }
                message.text = XmlUtil.serialize(html)
                message.setHeader('Content-Type', 'text/html')
            }
        }
    }

}
