package com.github.zobkov.webhook

import groovy.transform.CompileStatic
import org.apache.camel.Expression
import org.apache.camel.builder.RouteBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * A route that translates events coming from github.
 */
@CompileStatic
@Component
class GHEventsTranslatorRoute extends RouteBuilder {

    @Autowired
    @Qualifier(value = 'translator')
    Expression translator

    @Override
    void configure() throws Exception {
        from('seda:github-events').id('translator')
                .transform(translator).id('translate-github-events')
                .to('seda:email-sender').id('to-email-sender')
    }

}
