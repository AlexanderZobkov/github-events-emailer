package com.github.gee

import groovy.transform.CompileStatic
import org.kohsuke.github.GHEventInfo
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub

import java.time.Instant

/**
 * Retrieves events from GitHub by polling them from Events API.
 *
 * eventsIteratorSupplier is supposed to be return {@link org.kohsuke.github.PagedIterator} from
 * {@link GHRepository} or {@link org.kohsuke.github.GHOrganization}.
 *
 * https://docs.github.com/en/developers/webhooks-and-events/events/github-event-types
 */
@CompileStatic
class SimpleGHEventsPoller implements GHEventsPoller {

    Closure<Iterator<GHEventInfo>> eventsIteratorSupplier

    Instant since

    List<GHEventInfo> receive() throws IOException {
        Iterator<GHEventInfo> iterator = eventsIteratorSupplier.call()
        List<GHEventInfo> newEventInfos = iterator.takeWhile { isNewEvent(it) }.toList()
        if (newEventInfos) {
            since = newEventInfos.first().createdAt.toInstant()
        }
        return newEventInfos
    }

    // XXX: Probably the condition should be >= but need to drop already seen events
    private boolean isNewEvent(GHEventInfo eventInfo) {
        eventInfo.createdAt.toInstant().toEpochMilli() > this.since.toEpochMilli()
    }

}
