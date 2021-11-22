package com.github.gee

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.xml.XmlParser
import groovy.xml.XmlUtil
import org.apache.camel.Exchange
import org.apache.camel.Expression
import javax.mail.internet.MimeMessage

/**
 * Appends debug/troubleshoot information to the body of {@link MimeMessage}.
 *
 * Adds X-GitHub-Delivery header (if provided) from a webhook request from GitHub as email body.
 * It supports plain text and html emails.
 *
 * Expects a list of {@link MimeMessage} as input.
 */
@CompileStatic
class DebugInfoBodyAppender implements Expression {

    @Override
    <T> T evaluate(Exchange exchange, Class<T> type) {
        List<MimeMessage> body = exchange.message.getMandatoryBody(List<MimeMessage>)
        List<MimeMessage> answer = body.collect { MimeMessage mimeMessage ->
            appendTail(mimeMessage, exchange)
            mimeMessage
        }
        return type.cast(answer)
    }

    protected String calculateTailText(Exchange exchange) {
        List<String> tail = ['X-GitHub-Delivery',].collect { String header ->
            Object value = exchange.message.headers.get(header)
            value ? "${header}: ${value}" : null
        }.grep() as List<String>
        return tail ? (['---'] + tail).join('\n') : null
    }

    private void appendTail(MimeMessage message, Exchange exchange) {
        String tail = calculateTailText(exchange)
        if (tail) {
            AppenderFactory.create(message).appendText(tail)
        }
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
