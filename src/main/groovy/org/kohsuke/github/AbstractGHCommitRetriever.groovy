package org.kohsuke.github

import groovy.transform.CompileStatic
import org.kohsuke.github.function.InputStreamFunction

/**
 * Base class to retrieve a commit from the Github.
 *
 * API like {@link GHRepository#getCommit(java.lang.String)} does not allow to specify
 * a media type to retrieve a commit in other format than json.
 *
 * The class is located in org.kohsuke.github package to be able to use package visible API of the package.
 */
@CompileStatic
abstract class AbstractGHCommitRetriever {

    /**
     * Gets a commit from the repository.
     *
     * @param repo the repo where to look for a commit
     * @param sha1 the sha1 of the commit
     *
     * @return the commit in a format prescribed by the {@link #mediaType()}.
     * @throws IOException if io exception occurred.
     */
    String getCommit(GHRepository repo, String sha1) throws IOException {
        Requester requester = repo.root.createRequest()
        String commit = requester
                .withPreview(mediaType())
                .withUrlPath("/repos/${repo.ownerName}/${repo.name}/commits/${sha1}")
                .fetchStream responseHandler()
        return commit
    }

    /**
    * Returns one of https://docs.github.com/en/rest/overview/media-types#commits-commit-comparison-and-pull-requests
    */
    abstract String mediaType()

    /**
     * A function that reads the commit from GitHub response {@link InputStream} into {@link String}.
     */
    abstract InputStreamFunction responseHandler()

}
