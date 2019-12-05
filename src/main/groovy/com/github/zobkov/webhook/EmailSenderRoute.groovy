package com.github.zobkov.webhook

import com.sun.mail.util.MailConnectException
import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.builder.RouteBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import javax.mail.internet.MimeMessage

/**
 * A route that sends emails.
 */
@CompileStatic
@Component
class EmailSenderRoute extends RouteBuilder {

    @Value('${recipient}')
    String recipient

    @Value('${smtp.server.host}')
    String smtpServerHost

    @Value('${smtp.server.port}')
    int smtpServerPort

    @Value('${smtp.server.connection.timeout}')
    String smtpServerConnectionTimeout

    @Value('${smtp.server.redelivery.attempts}')
    int smtpServerRedeliveryAttempts

    @Value('${smtp.server.redelivery.timeout}')
    int smtpServerRedeliveryDelay

    void configure() throws Exception {
        onException(MailConnectException)
                .maximumRedeliveries(smtpServerRedeliveryAttempts)
                .maximumRedeliveryDelay(smtpServerRedeliveryDelay)

        from('seda:email-sender')
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
                exchange.in.body = message.content as String
                exchange.in.headers.'To' = recipient
                exchange.in.headers.'From' = message.from.first().toString()
                exchange.in.headers.'Subject' = message.subject
            }
        }
    }

}
