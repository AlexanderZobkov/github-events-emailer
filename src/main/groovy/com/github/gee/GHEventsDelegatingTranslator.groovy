package com.github.gee

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.kohsuke.github.GHEventPayload
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 *  Translator that delegates translation of specific type of {@link GHEventPayload} event
 *  to a specific translator based on {@link Expression}.
 */
@Component(value = 'translator')
@CompileStatic
class GHEventsDelegatingTranslator implements Expression {

    /**
     * App config.
     */
    @Autowired
    AppConfig appConfig

    @Override
    <T> T evaluate(Exchange exchange, Class<T> type) {
        GHEventPayload payload = exchange.in.getMandatoryBody(GHEventPayload)
        Expression delegate = appConfig.translationMap()[payload.class as Class]
        return delegate.evaluate(exchange, type)
    }

}
