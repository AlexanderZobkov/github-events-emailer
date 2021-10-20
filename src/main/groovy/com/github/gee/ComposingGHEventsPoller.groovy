package com.github.gee

import groovy.transform.CompileStatic
import org.kohsuke.github.GHEventInfo

/**
 * Composes multiple {@link GHEventsPoller}s into one.
 * These pollers are executed one by one in one thread
 * and results from each other are appended.
 */
@CompileStatic
class ComposingGHEventsPoller implements GHEventsPoller {

    List<GHEventsPoller> pollers

    @Override
    List<GHEventInfo> receive() throws IOException {
        pollers.collectMany { it.receive() } as List<GHEventInfo>
    }
}
