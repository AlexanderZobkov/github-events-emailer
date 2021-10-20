package com.github.gee

import groovy.transform.CompileStatic
import org.kohsuke.github.GHEventInfo

/**
 * Retrieves the next available {@link GHEventInfo}s.
 */
@CompileStatic
interface GHEventsPoller {

    /**
     * Retrieves a list of the next available {@link GHEventInfo}.
     *
     * Each call to this method attempts to retrieve next available events.
     * The method does not wait and returns immediate if no new
     * events are available yet.
     *
     * @return List of {@link GHEventInfo} or empty list if no new events.
     * @throws IOException when IO exception is occurred.
     */
    List<GHEventInfo> receive() throws IOException
}
