package org.kohsuke.github

import org.apache.commons.io.IOUtils
import spock.lang.Specification

import java.nio.charset.Charset

class ClippingGHCommitDiffRetrieverTest extends Specification {

    String diff = '''\
diff line 1
差异线 2
Differenzlinie 3'''

    def "can retrieve only part of diff"() {
        given:
        ClippingGHCommitDiffRetriever retriever = new ClippingGHCommitDiffRetriever()
        retriever.maxChars = 14
        InputStream is = IOUtils.toInputStream(diff, Charset.defaultCharset())
        when:
        String fetchedDiff = retriever.responseHandler().apply(is)
        then:
        with(fetchedDiff.readLines()) {
            it.size() == 4
            it[0] == 'diff line 1'
            it[1] == '差异'
            it[2] == '...'
            it[3] == '[Diff clipped after 14 chars]'
        }
    }

    def "can retrieve whole diff"() {
        given:
        AbstractGHCommitRetriever retriever = new ClippingGHCommitDiffRetriever()
        retriever.maxChars = -1
        InputStream is = IOUtils.toInputStream(diff, Charset.defaultCharset())
        when:
        String fetchedDiff = retriever.responseHandler().apply(is)
        then:
        fetchedDiff == diff
    }

}
