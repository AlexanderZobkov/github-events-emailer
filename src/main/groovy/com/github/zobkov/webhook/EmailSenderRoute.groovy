package com.github.zobkov.webhook

import com.sun.mail.util.MailConnectException
import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.builder.RouteBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.lang.NonNull
import org.springframework.stereotype.Component

import javax.mail.internet.MimeMessage

/**
 * A route that sends emails.
 *
 * Expects a {@link List} of {@link MimeMessage} as a body.
 * Each {@link MimeMessage} must be set with: from, subject and content.
 *
 * Each {@link MimeMessage} will be sent as a single email.
  */
@CompileStatic
@Component
class EmailSenderRoute extends RouteBuilder {

    @NonNull
    @Value('${smtp.recipients}')
    String recipients

    @NonNull
    @Value('${smtp.server.host}')
    String smtpServerHost

    @Value('${smtp.server.port}')
    int smtpServerPort

    @Value('${smtp.server.connection.timeout}')
    int smtpServerConnectionTimeout

    @Value('${smtp.server.redelivery.attempts}')
    int smtpServerRedeliveryAttempts

    @Value('${smtp.server.redelivery.timeout}')
    int smtpServerRedeliveryDelay

    void configure() throws Exception {
        from('seda:email-sender').id('email-sender')
                .onException(MailConnectException)
                    .maximumRedeliveries(smtpServerRedeliveryAttempts)
                    .maximumRedeliveryDelay(smtpServerRedeliveryDelay)
                .end()
                .split(body())
                .removeHeaders('*')
                .process(prepareEmail())
                .to("smtp://admin@${smtpServerHost}:${smtpServerPort}?" +
                        "password=secret&debugMode=true&connectionTimeout=${smtpServerConnectionTimeout}")
    }

    Processor prepareEmail() {
        return new Processor() {
            void process(Exchange exchange) throws Exception {
                MimeMessage message = exchange.in.getMandatoryBody(MimeMessage)
                assert message.content
                exchange.in.body = message.content as String
                exchange.in.headers.'To' = recipients
                assert message.from
                exchange.in.headers.'From' = message.from.first().toString()
                assert message.subject
                exchange.in.headers.'Subject' = message.subject
            }
        }
    }

}
