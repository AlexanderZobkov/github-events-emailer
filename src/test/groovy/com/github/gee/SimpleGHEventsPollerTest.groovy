package com.github.gee

import org.kohsuke.github.GHEvent
import org.kohsuke.github.GHEventInfo
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GHUser
import spock.lang.Specification

import java.time.Instant

class SimpleGHEventsPollerTest extends Specification {

    GHUser ghUser = Mock()
    GHRepository ghRepository = Mock()

    def "No events are returned"() {
        given:
        SimpleGHEventsPoller poller = new SimpleGHEventsPoller(eventsIteratorSupplier: { [].iterator() },
                since: Instant.ofEpochSecond(1))
        when:
        List<GHEventInfo> events = poller.receive()
        then:
        events.size() == 0
    }

    def "All events are old"() {
        given:
        GHEventInfo eventInfo1 = Mock()
        GHEventInfo eventInfo2 = Mock()
        GHEventInfo eventInfo3 = Mock()

        SimpleGHEventsPoller poller = new SimpleGHEventsPoller(
                eventsIteratorSupplier: { [eventInfo1, eventInfo2, eventInfo3].iterator() },
                since: Instant.ofEpochSecond(10)
        )

        when:
        List<GHEventInfo> eventsFirstPull = poller.receive()
        List<GHEventInfo> eventsSecondPull = poller.receive()
        then:
        eventsFirstPull.size() == 0
        eventsSecondPull.size() == 0

        ghRepository.fullName >> 'mega-man-repo'

        eventInfo1.getCreatedAt() >> Date.from(Instant.ofEpochSecond(3))
        eventInfo1.getType() >> GHEvent.PUSH
        eventInfo1.getPayload(_) >> new GHEventPayload.Push()
        eventInfo1.getActor() >> ghUser
        eventInfo1.getRepository() >> ghRepository

        eventInfo2.getCreatedAt() >> Date.from(Instant.ofEpochSecond(2))
        eventInfo2.getType() >> GHEvent.PUSH
        eventInfo2.getPayload(_) >> new GHEventPayload.Push()
        eventInfo2.getActor() >> ghUser
        eventInfo2.getRepository() >> ghRepository

        eventInfo3.getCreatedAt() >> Date.from(Instant.ofEpochSecond(1))
        eventInfo3.getType() >> GHEvent.PUSH
        eventInfo3.getPayload(_) >> new GHEventPayload.Push()
        eventInfo3.getActor() >> ghUser
        eventInfo3.getRepository() >> ghRepository
    }

    def "All events are new"() {
        given:
        GHEventInfo eventInfo1 = Mock()
        GHEventInfo eventInfo2 = Mock()
        GHEventInfo eventInfo3 = Mock()

        SimpleGHEventsPoller poller = new SimpleGHEventsPoller(
                eventsIteratorSupplier: { [eventInfo1, eventInfo2, eventInfo3].iterator() },
                since: Instant.ofEpochSecond(10)
        )
        when:
        List<GHEventInfo> eventsFirstPull = poller.receive()
        List<GHEventInfo> eventsSecondPull = poller.receive()
        then:
        eventsFirstPull.size() == 3
        eventsSecondPull.size() == 0

        ghRepository.fullName >> 'mega-man-repo'

        eventInfo1.getCreatedAt() >> Date.from(Instant.ofEpochSecond(13))
        eventInfo1.getType() >> GHEvent.PUSH
        eventInfo1.getPayload(_) >> new GHEventPayload.Push()
        eventInfo1.getActor() >> ghUser
        eventInfo1.getRepository() >> ghRepository

        eventInfo2.getCreatedAt() >> Date.from(Instant.ofEpochSecond(12))
        eventInfo2.getType() >> GHEvent.PUSH
        eventInfo2.getPayload(_) >> new GHEventPayload.Push()
        eventInfo2.getActor() >> ghUser
        eventInfo2.getRepository() >> ghRepository

        eventInfo3.getCreatedAt() >> Date.from(Instant.ofEpochSecond(11))
        eventInfo3.getType() >> GHEvent.PUSH
        eventInfo3.getPayload(_) >> new GHEventPayload.Push()
        eventInfo3.getActor() >> ghUser
        eventInfo3.getRepository() >> ghRepository
    }

}
