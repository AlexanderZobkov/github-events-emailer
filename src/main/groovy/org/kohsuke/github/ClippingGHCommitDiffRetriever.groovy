package org.kohsuke.github

import groovy.transform.CompileStatic
import org.apache.commons.io.IOUtils
import org.kohsuke.github.function.InputStreamFunction

/**
 * A class that retrieve a commit in the diff format instead of the json.
 * Also the class allows to clip the diff up to specified number of characters.
 */
@CompileStatic
class ClippingGHCommitDiffRetriever extends AbstractGHCommitRetriever {

    /**
     * Maximum number of chars to read when retrieving a commit diff from GitHub.
     * Setting the value to -1 will read the whole commit diff as provided by GitHub.
     */
    int maxChars

    @Override
    String mediaType() {
        'application/vnd.github.v3.diff'
    }

    @Override
    InputStreamFunction<String> responseHandler() {
        { InputStream is ->
            InputStreamReader reader = new InputStreamReader(is)
            StringWriter writer = new StringWriter(maxChars + 1)
            IOUtils.copyLarge(reader, writer, 0, maxChars)
            if (maxChars >= 0) {
                writer.toString() +
                        "\n...\n[Diff clipped after ${maxChars} chars]"
            } else {
                writer.toString()
            }
        } as InputStreamFunction<String>
    }

}
