package com.github.zobkov.webhook

import com.sun.mail.util.MailConnectException
import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.builder.RouteBuilder
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.lang.NonNull
import org.springframework.stereotype.Component

import javax.mail.internet.MimeMessage

/**
 * A route that sends emails.
 *
 * Expects a {@link List} of {@link MimeMessage} or just {@link MimeMessage} as a body.
 * Each {@link MimeMessage} must be set with: from, subject and content.
 *
 * Each {@link MimeMessage} will be sent as a single email, one by one in one thread.
  */
@CompileStatic
@Component
class EmailSenderRoute extends RouteBuilder {

    private static final Log LOG = LogFactory.getLog(EmailSenderRoute)

    @Value('${smtp.debug}')
    String smtpDebug

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
                        "password=secret&debugMode=${smtpDebug}&connectionTimeout=${smtpServerConnectionTimeout}")
    }

    Processor prepareEmail() {
        return new Processor() {
            void process(Exchange exchange) throws Exception {
                MimeMessage message = exchange.in.getMandatoryBody(MimeMessage)
                assert message.content
                exchange.in.body = message.content as String
                exchange.in.headers.'To' = recipients
                assert message.from
                String from = message.from.first()
                exchange.in.headers.'From' = from
                String subject = message.subject
                assert subject
                exchange.in.headers.'Subject' = subject
                LOG.debug("Preparing to send email: ${from} -> ${recipients} : ${subject}")
            }
        }
    }

}
