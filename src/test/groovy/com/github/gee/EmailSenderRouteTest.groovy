package com.github.gee

import com.sun.mail.smtp.SMTPAddressFailedException
import com.sun.mail.util.MailConnectException
import com.sun.mail.util.SocketConnectException
import org.apache.camel.EndpointInject
import org.apache.camel.Produce
import org.apache.camel.ProducerTemplate
import org.apache.camel.builder.AdviceWith
import org.apache.camel.builder.AdviceWithRouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.spring.SpringCamelContext
import org.apache.camel.spring.javaconfig.CamelConfiguration
import org.apache.camel.test.spring.CamelSpringBootRunner
import org.apache.camel.test.spring.UseAdviceWith
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests

import javax.mail.MessagingException
import javax.mail.SendFailedException
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

@RunWith(CamelSpringBootRunner.class)
@ContextConfiguration(classes = [ContextConfig])
@UseAdviceWith
class EmailSenderRouteTest extends AbstractJUnit4SpringContextTests {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint

    @Produce("seda:email-sender")
    protected ProducerTemplate template

    @Autowired
    SpringCamelContext camelContext

    @Before
    void setup() {
        AdviceWith.adviceWith(camelContext.getRouteDefinition("email-sender"), camelContext, new AdviceWithRouteBuilder() {
            @Override
            void configure() throws Exception {
                weaveByToUri('smtp.*').replace().to("mock:result")
            }
        })
        camelContext.start()
        template.start()
    }

    @DirtiesContext
    @Test
    void testSendOneEmail() throws Exception {
        resultEndpoint.expectedBodiesReceived('commit')
        template.sendBody(buildMimeMessage('commit'))
        resultEndpoint.assertIsSatisfied()
    }

    @DirtiesContext
    @Test
    void testListEmailsEmpty() throws Exception {
        resultEndpoint.expectedMessageCount(0)
        template.sendBody([])
        resultEndpoint.assertIsSatisfied()
    }

    @DirtiesContext
    @Test
    void testListEmailsMultiple() throws Exception {
        resultEndpoint.expectedBodiesReceived((1..3).collect { 'commit_' + it })
        template.sendBody((1..3).collect { buildMimeMessage('commit_' + it) })
        resultEndpoint.assertIsSatisfied();
    }

    @DirtiesContext
    @Test
    void testNoResponseFromServer() throws Exception {
        resultEndpoint.resultWaitTime = 10000
        resultEndpoint.expectedMessageCount(3)
        resultEndpoint.whenAnyExchangeReceived {
            throw new MessagingException('Exception reading response',
                    new SocketTimeoutException('Read timed out'))
        }
        template.sendBody((1..10).collect { buildMimeMessage('commit_' + it) })
        resultEndpoint.assertIsSatisfied()
        List<String> expectedBodies = Collections.nCopies(3, 'commit_1')
        List<String> actualBodies = resultEndpoint.receivedExchanges.collect {
            it.message.getMandatoryBody(MimeMessage).getContent() as String
        }
        Assert.assertEquals(expectedBodies, actualBodies);
    }

    @DirtiesContext
    @Test
    void testCantConnectServer() throws Exception {
        resultEndpoint.resultWaitTime = 10000
        resultEndpoint.expectedMessageCount(3)
        resultEndpoint.whenAnyExchangeReceived {
            throw new MailConnectException(
                    new SocketConnectException('Connection refused: connect',
                            new SocketTimeoutException('connect timed out'),
                            'smtp.server', 1, -1))
        }
        template.sendBody((1..3).collect { buildMimeMessage('commit_' + it) })
        resultEndpoint.assertIsSatisfied()
        List<String> expectedBodies = Collections.nCopies(3, 'commit_1')
        List<String> actualBodies = resultEndpoint.receivedExchanges.collect {
            it.message.getMandatoryBody(MimeMessage).getContent() as String
        }
        Assert.assertEquals(expectedBodies, actualBodies);
    }

    @DirtiesContext
    @Test
    void testSendFailed() throws Exception {
        resultEndpoint.resultWaitTime = 10000
        resultEndpoint.expectedMessageCount(3)
        resultEndpoint.whenAnyExchangeReceived {
            throw new SendFailedException('Invalid Addresses',
                    new SMTPAddressFailedException(
                            new InternetAddress('<UPDATE_ME>'),
                            'MAIL', 504,
                            '504 5.5.2 <UPDATE_ME>: Sender address rejected: need fully-qualified address'))
        }
        List<MimeMessage> emailsToSent = (1..3).collect { buildMimeMessage('commit_' + it) }
        template.sendBody(emailsToSent)
        resultEndpoint.assertIsSatisfied()
        List<String> expectedBodies = emailsToSent.collect { it.getContent() as String }
        List<String> actualBodies = resultEndpoint.receivedExchanges.collect {
            it.message.getMandatoryBody(MimeMessage).getContent() as String
        }
        Assert.assertEquals(expectedBodies, actualBodies)
    }

    private MimeMessage buildMimeMessage(String body) {
        new MimeMessage(null).tap {
            from = 'email@domain.org'
            subject = 'subject'
            text = body
        }
    }

    @Configuration
    @Import(EmailSenderRoute)
    static class ContextConfig extends CamelConfiguration {

        @Bean
        static PropertySourcesPlaceholderConfigurer properties() {
            new PropertySourcesPlaceholderConfigurer().tap {
                it.locations = [
                        new ClassPathResource('application.properties'),

                ] as Resource[]
                it.ignoreResourceNotFound = false
                it.ignoreUnresolvablePlaceholders = false
                it.trimValues = true
            }
        }
    }
}