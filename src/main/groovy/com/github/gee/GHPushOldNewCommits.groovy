package com.github.gee

import groovy.transform.CompileStatic
import org.apache.camel.Expression
import org.apache.commons.lang3.tuple.Pair
import org.kohsuke.github.GHCommit

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Splits list of commits into old and new ones by {@link GHCommit#getCommitDate()}.
 */
@CompileStatic
class GHPushOldNewCommits extends AbstractGHPushSplitter {

    Expression perCommitMimeMessages
    Expression digestMimeMessage

    int maxCommitAge

    @Override
    protected List<Pair<List<GHCommit>, Expression>> splitCommits(List<GHCommit> pushCommits) {
        List<List<GHCommit>> newAndOld = pushCommits.split { GHCommit commit ->
            Instant commitDate = commit.commitDate.toInstant()
            long commitAge = Duration.between(commitDate, ZonedDateTime.now()).toDays()
            commitAge < maxCommitAge
        }
        return [Pair.of(newAndOld[0], perCommitMimeMessages),
                Pair.of(newAndOld[1], digestMimeMessage)]
    }
}
