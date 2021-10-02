package com.github.gee

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Expression
import org.apache.commons.lang3.tuple.Pair
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GHRepository

import javax.mail.internet.MimeMessage

/**
 * Splits a list of commits in {@link GHEventPayload.Push} and processes them individually.
 */
@CompileStatic
abstract class AbstractGHPushSplitter implements Expression {

    @Override
    <T> T evaluate(Exchange exchange, Class<T> type) {
        GHEventPayload.Push event = exchange.in.getMandatoryBody(GHEventPayload.Push)
        List<GHCommit> ghCommits = retrieveGHCommits(event.repository, event.commits)
        List<Pair<List<GHCommit>, Expression>> splits = splitCommits(ghCommits)
        List<MimeMessage> result = splits.collectMany { Pair<List<GHCommit>, Expression> p ->
            p.right.evaluate(buildNewExchange(exchange, event, p.left), List<MimeMessage>)
        } as List<MimeMessage>
        return type.cast(result)
    }

    /**
     * Splits a list of {@link GHCommit} into multiple lists and assign {@link Expression} to process this or that list.
     * @param pushCommits {@link GHCommit} to split
     * @return List of pairs where pair.left contains list of {@link GHCommit}
     * and pair.right contains {@link Expression} to process that list.
     */
    abstract protected List<Pair<List<GHCommit>, Expression>> splitCommits(List<GHCommit> pushCommits)

    private Exchange buildNewExchange(Exchange exchange, GHEventPayload.Push event, List<GHCommit> commits) {
        Exchange answer = exchange.copy()
        answer.message.body = new ExpandedGHPushEvent(
                event: event,
                commits: commits)
        return answer
    }

    private List<GHCommit> retrieveGHCommits(GHRepository repository,
                                             List<GHEventPayload.Push.PushCommit> pushCommits) {
        return pushCommits.collect { GHEventPayload.Push.PushCommit commit ->
            repository.getCommit(commit.sha)
        }
    }

    static class ExpandedGHPushEvent {
        GHEventPayload.Push event
        List<GHCommit> commits
    }
}
