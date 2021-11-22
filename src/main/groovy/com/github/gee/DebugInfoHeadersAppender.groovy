package com.github.gee

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Expression

import javax.mail.internet.MimeMessage

/**
 * Appends debug/troubleshoot information as headers of {@link MimeMessage}.
 *
 * Adds X-GitHub-ABC headers (if any) from a webhook request from GitHub as SMTP headers.
 * Adds X-<App-name>-Version and X-<App-name>-CommitId headers with the specified values.
 *
 * Expects a list of {@link MimeMessage} as input.
 */
@CompileStatic
class DebugInfoHeadersAppender implements Expression  {

    String name
    String version
    String commitId

    @Override
    <T> T evaluate(Exchange exchange, Class<T> type) {
        List<MimeMessage> body = exchange.message.getMandatoryBody(List<MimeMessage>)
        List<MimeMessage> answer = body.collect { MimeMessage mimeMessage ->
            populateHeaders(mimeMessage, exchange)
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
        message.setHeader("X-${name.capitalize()}-Version", version)
        message.setHeader("X-${name.capitalize()}-CommitId", commitId)
    }
}
