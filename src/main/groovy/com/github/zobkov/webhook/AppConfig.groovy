package com.github.zobkov.webhook

import groovy.transform.CompileStatic
import org.apache.camel.CamelContext
import org.apache.camel.Expression
import org.apache.camel.spring.boot.CamelContextConfiguration
import org.kohsuke.github.GHCommitDiffRetriever
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configures Webhook app.
 */
@CompileStatic
@Configuration
class AppConfig {

    @Value('${github.endpoint}')
    String githubEndpoint

    @Value('${github.oauthToken}')
    String githubOAuthToken

    /**
     * Allows access to Github API.
     */
    @Bean
    GitHub github() {
        GitHub gitHub = new GitHubBuilder()
                .withEndpoint(githubEndpoint)
                .withOAuthToken(githubOAuthToken)
                .build()
        gitHub.checkApiUrlValidity()
        return gitHub
    }

    @Bean
    GHCommitDiffRetriever commitRetriever() {
        return new GHCommitDiffRetriever()
    }

    @Value('${push_event.commits.compact.maxCommitAge}')
    int maxCommitAge

    @Bean
    Map<Class, ? extends Expression> translationMap() {
        return [
                (GHEventPayload.Push)  : new GHPushToMimeMessage(commitDiffRetriever: commitRetriever(),
                        maxCommitAge: maxCommitAge),
                (GHEventPayload.Create): new GHCreateToMimeMessage(),
        ]
    }

    @Bean
    CamelContextConfiguration contextConfiguration() {
        return new CamelContextConfiguration() {
            @Override
            void beforeApplicationStart(CamelContext context) {
                context.logExhaustedMessageBody = true
            }

            @Override
            void afterApplicationStart(CamelContext camelContext) {
                // Do nothing
            }
        }
    }
}
