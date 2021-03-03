package org.kohsuke.github

import groovy.transform.CompileStatic

/**
 * Retrieves diff of the commit.
 *
 * The class is located in org.kohsuke.github package to be able to use package visible API of the package.
 */
@CompileStatic
class GHCommitDiffRetriever {

    /**
     * Gets a commit from the repository.
     *
     * @param repo the repo where to look for a commit
     * @param sha1 the sha 1
     *
     * @return the commit diff.
     * @throws IOException the io exception.
     */
    String getCommit(GHRepository repo, String sha1) throws IOException {
        Requester requester = repo.root.createRequest()
        InputStream diff = requester
                .withPreview('application/vnd.github.v3.diff')
                .withUrlPath("/repos/${repo.ownerName}/${repo.name}/commits/${sha1}")
                .fetchStream { Requester.copyInputStream(it) }
        return diff.text
    }

}
