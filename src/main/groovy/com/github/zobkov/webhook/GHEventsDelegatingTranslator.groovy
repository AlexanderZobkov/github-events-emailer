package com.github.zobkov.webhook

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.kohsuke.github.GHCommitDiffRetriever
import org.kohsuke.github.GHEventPayload
import org.springframework.stereotype.Component

/**
 *  Translator that delegates translation of specific type of {@link GHEventPayload} event
 *  to a specific translator based on {@link Expression}.
 */
@Component(value = 'translator')
@CompileStatic
class GHEventsDelegatingTranslator implements Expression {

    /**
     * Map that contains translators for github events.
     */
    @SuppressWarnings('UnnecessaryCast')
    Map<Class, ? extends Expression> translationMap =
            [
                    (GHEventPayload.Push)  : new GHPushToMimeMessage(commitRetriever: new GHCommitDiffRetriever()),
                    (GHEventPayload.Create): new GHCreateToMimeMessage(),
            ] as Map<Class, ? extends Expression>

    @Override
    <T> T evaluate(Exchange exchange, Class<T> type) {
        GHEventPayload payload = exchange.in.getMandatoryBody(GHEventPayload)
        Expression expression = translationMap[payload.class as Class]
        return expression.evaluate(exchange, type)
    }

}
