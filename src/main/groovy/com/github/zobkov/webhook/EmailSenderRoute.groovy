package com.github.zobkov.webhook

import com.sun.mail.util.MailConnectException
import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.builder.RouteBuilder
import org.springframework.beans.factory.annotation.Value
import javax.annotation.Nonnull
import org.springframework.stereotype.Component

import javax.mail.Message
import javax.mail.internet.InternetAddress
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

    @Value('${smtp.debug}')
    String smtpDebug

    @Nonnull
    @Value('${smtp.recipients}')
    String recipients

    @Nonnull
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
                .split(body()).id('split-emails-list')
                .removeHeaders('*').id('remove-headers-sending-email')
                .process(prepareEmail()).id('prepare-email')
                .to("smtp://admin@${smtpServerHost}:${smtpServerPort}?" +
                        "password=secret&debugMode=${smtpDebug}&" +
                        "connectionTimeout=${smtpServerConnectionTimeout}").id('send-email')
    }

    Processor prepareEmail() {
        return new Processor() {
            void process(Exchange exchange) throws Exception {
                MimeMessage message = exchange.in.getMandatoryBody(MimeMessage)
                message.setRecipient(Message.RecipientType.TO, InternetAddress.parse(recipients).first())
            }
        }
    }

}
