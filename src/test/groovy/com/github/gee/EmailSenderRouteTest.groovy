package com.github.gee

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