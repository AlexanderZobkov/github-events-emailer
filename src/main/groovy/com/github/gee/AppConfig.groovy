package com.github.gee

import groovy.transform.CompileStatic
import org.apache.camel.CamelContext
import org.apache.camel.Expression
import org.apache.camel.component.micrometer.messagehistory.MicrometerMessageHistoryFactory
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory
import org.apache.camel.spring.boot.CamelContextConfiguration
import org.kohsuke.github.AbstractGHCommitRetriever
import org.kohsuke.github.ClippingGHCommitDiffRetriever
import org.kohsuke.github.GHOrganization
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import java.time.Instant

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

    @ConditionalOnProperty(
            value = 'method.retrieve.events',
            havingValue = 'poller',
            matchIfMissing = false)
    @Bean
    GHEventsPoller ghEventsPoller() {
        Instant since = Instant.now().minusMillis(backwardOffset)
        int pageSize = 30
        List<GHEventsPoller> orgsPollers = organizations.collect { String name ->
            GHOrganization org = github().getOrganization(name)
            new SimpleGHEventsPoller(eventsIteratorSupplier: { org.listEvents().withPageSize(pageSize).iterator() },
                    since: since) as GHEventsPoller
        }
        List<GHEventsPoller> reposPollers = repositories.collect { String name ->
            GHRepository repo = github().getRepository(name)
            new SimpleGHEventsPoller(eventsIteratorSupplier: { repo.listEvents().withPageSize(pageSize).iterator() },
                    since: since) as GHEventsPoller
        }
        new ComposingGHEventsPoller(pollers: orgsPollers + reposPollers)
    }

    @Value('${poller.backwardOffset}')
    long backwardOffset

    @Value('${poller.organizations}')
    List<String> organizations

    @Value('${poller.repositories}')
    List<String> repositories

    @Bean
    AbstractGHCommitRetriever commitRetriever() {
        return new ClippingGHCommitDiffRetriever(maxChars: maxChar)
    }

    @Value('${push_event.commits.compact.maxCommitAge}')
    int maxCommitAge

    @Value('${push_event.commits.diff.maxChar}')
    int maxChar

    @Bean
    Map<String, ? extends Expression> translationMap() {
        return [
                push  : new GHPushOldNewCommits(
                                perCommitMimeMessages: new PerPushCommit(commitDiffRetriever: commitRetriever()),
                                digestMimeMessage: new DigestPushCommit(),
                                maxCommitAge: maxCommitAge),
                create: new GHCreateToMimeMessage(gitHub: github()),
                delete: new GHDeleteToMimeMessage(gitHub: github()),
                fork  : new GHForkToMimeMessage(gitHub: github()),
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
