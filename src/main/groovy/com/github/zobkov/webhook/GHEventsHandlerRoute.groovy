package com.github.zobkov.webhook

import groovy.transform.CompileStatic
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.builder.RouteBuilder
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHEventPayload
import org.kohsuke.github.GHRef
import org.kohsuke.github.GHTagObject
import org.kohsuke.github.GitUser
import org.kohsuke.github.GHCommitDiffRetriever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * A route that handles events coming from github.
 */
@SuppressWarnings('DuplicateStringLiteral')
@SuppressWarnings('DuplicateListLiteral')
@CompileStatic
@Component
class GHEventsHandlerRoute extends RouteBuilder {

    @Override
    void configure() throws Exception {
        from('seda:github-events')
                .process(translateEvent())
                .to('seda:email-sender')
    }

    @Autowired
    GHCommitDiffRetriever commitRetriever

    Processor translateEvent() {
        return new Processor() {
            void process(Exchange exchange) throws Exception {
                GHEventPayload payload = exchange.in.getMandatoryBody(GHEventPayload)
                Closure translator = translationMap[payload.class as Class]
                exchange.in.body = translator.call(exchange, payload)
            }
        }
    }

    Closure<List<MimeMessage>> push = { Exchange exchange, GHEventPayload.Push event ->
        event.commits.collect { GHEventPayload.Push.PushCommit commit ->
            new MimeMessage((Session) null).tap {
                from = InternetAddress.parse(event.pusher.email).first()
                subject = 'New commit in repository ' +
                        "${event.repository.fullName}/${event.ref - 'refs/heads/' - 'refs/tags/'} " +
                        "- ${commit.sha.take(7)} ${commit.message.readLines().first().take(50)}"
                text = ({
                    StringBuilder builder = new StringBuilder()

                    String sha = commit.sha

                    builder << "URL: ${commit.url}\n"
                    builder << "SHA: ${sha}\n"

                    GHCommit.ShortInfo shortInfo = event.repository
                            .getCommit(sha)
                            .commitShortInfo
                    GitUser author = shortInfo.author
                    builder << "Author: ${[author.name, author.email].join(' / ')}\n"
                    builder << "AuthorDate: ${shortInfo.authoredDate}\n"
                    GitUser commiter = shortInfo.committer
                    builder << "Commit: ${[commiter.name, commiter.email].join(' / ')}\n"
                    builder << "CommitDate: ${shortInfo.commitDate}\n"

                    builder << "Message: ${shortInfo.message}\n"

                    builder << '---\n'
                    builder << 'Files:\n'
                    builder << event.repository
                            .getCommit(sha)
                            .files
                            .collect { GHCommit.File file -> "${file.status.capitalize()} ${file.fileName}" }
                            .join('\n') + '\n'

                    builder << '---\n'
                    builder << commitRetriever.getCommit(event.repository, sha) + '\n'

                    builder << '---\n'
                    ['X-GitHub-Delivery'].each { String header ->
                        builder << "${header}: ${exchange.in.getHeader(header, 'is absent')}\n"
                    }

                    builder.toString()
                } as Closure<String>)()
            }
        }
    }

    Closure<List<MimeMessage>> create = { Exchange exchange, GHEventPayload.Create event ->
        [new MimeMessage((Session) null).tap {
            String refType = event.refType
            String ref = event.ref

            subject = "New ${refType} in repository ${event.repository.fullName} - ${event.ref}"
            text = ({
                StringBuilder builder = new StringBuilder()

                switch (refType) {
                    case 'tag':
                        GHRef tagRef = event.repository.getRefs('tag')
                                .find { GHRef tag -> tag.ref - 'refs/heads/' - 'refs/tags/' == ref }
                        try {
                            GHTagObject annotatedTag = event.repository.getTagObject(tagRef.object.sha)
                            // TODO: builder << "URL: ${annotatedTag.url}\n"
                            GitUser tagger = annotatedTag.tagger
                            builder << "Annotated tag: ${ref}\n"
                            builder << "Tagger: ${[tagger.name, tagger.email].join(' / ')}\n"
                            builder << "Message: ${annotatedTag.message}\n"
                            from = tagger.email
                        } catch (FileNotFoundException e) {
                            // TODO: builder << "URL: ${tagRef.url}\n"
                            GHCommit commit = event.repository.getCommit(tagRef.object.sha)
                            GitUser author = commit.commitShortInfo.author
                            builder << "Lightweight tag: ${ref}\n"
                            builder << "Tagger: ${[author.name, author.email].join(' / ')}\n"
                            builder << "Message: ${commit.commitShortInfo.message}\n"
                            from = author.email
                        }
                        break
                    case 'branch':
                //GHBranch branch = event.repository.getBranch(ref)
                //break
                    default:
                        throw new UnsupportedOperationException("Not supported: ${refType}")
                }

                builder << '---\n'
                ['X-GitHub-Delivery'].each { String header ->
                    builder << "${header}: ${exchange.in.getHeader(header, 'is absent')}\n"
                }

                builder.toString()
            } as Closure<String>)()
        }]
    }

    Closure<List<MimeMessage>> nop = { [] }

    Map<Class, Closure<List<MimeMessage>>> translationMap = [
            (GHEventPayload.Push)  : push,
            (GHEventPayload.Create): create,
            (GHEventPayload.Delete): nop,
    ]
}
