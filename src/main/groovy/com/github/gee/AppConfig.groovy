package com.github.gee

import groovy.transform.CompileStatic
import org.apache.camel.CamelContext
import org.apache.camel.Expression
import org.apache.camel.component.micrometer.messagehistory.MicrometerMessageHistoryFactory
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory
import org.apache.camel.spring.boot.CamelContextConfiguration
import org.kohsuke.github.GHCommitDiffRetriever
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configures the app.
 */
@CompileStatic
@Configuration
class AppConfig {

    @Value('${github.endpoint}')
    String githubEndpoint

    @Value('${github.oauthToken}')
    String githubOAuthToken

    @Value('${github.offline:false}')
    boolean githubOffline

    /**
     * Allows access to Github API.
     */
    @Bean
    GitHub github() {
        GitHub answer
        if (githubOffline) {
            answer = GitHub.offline()
        } else {
            answer = new GitHubBuilder()
                    .withEndpoint(githubEndpoint)
                    .withOAuthToken(githubOAuthToken)
                    .build()
            answer.checkApiUrlValidity()
        }
        return answer
    }

    @Bean
    GHCommitDiffRetriever commitRetriever() {
        return new GHCommitDiffRetriever()
    }

    @Value('${push_event.commits.compact.maxCommitAge}')
    int maxCommitAge

    @Bean
    Map<String, ? extends Expression> translationMap() {
        return [
                push  : new DebugInfoAppender(delegate:
                        new GHPushOldNewCommits(
                                perCommitMimeMessages: new PerPushCommit(commitDiffRetriever: commitRetriever()),
                                digestMimeMessage: new DigestPushCommit(),
                                maxCommitAge: maxCommitAge)
                ),
                create: new DebugInfoAppender(delegate:
                        new GHCreateToMimeMessage(gitHub: github())),
                delete: new DebugInfoAppender(delegate:
                        new GHDeleteToMimeMessage(gitHub: github())),
                fork  : new DebugInfoAppender(delegate:
                        new GHForkToMimeMessage(gitHub: github())),
        ]
    }

    @Bean
    CamelContextConfiguration contextConfiguration() {
        return new CamelContextConfiguration() {
            @Override
            void beforeApplicationStart(CamelContext context) {
                context.logExhaustedMessageBody = true
                context.addRoutePolicyFactory(new MicrometerRoutePolicyFactory())
                context.messageHistoryFactory = new MicrometerMessageHistoryFactory()
            }

            @Override
            void afterApplicationStart(CamelContext camelContext) {
                // Do nothing
            }
        }
    }
}
