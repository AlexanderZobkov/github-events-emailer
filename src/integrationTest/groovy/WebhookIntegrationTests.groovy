import com.github.alexdlaird.ngrok.NgrokClient
import com.github.alexdlaird.ngrok.protocol.CreateTunnel
import com.github.alexdlaird.ngrok.protocol.Proto
import com.github.alexdlaird.ngrok.protocol.Tunnel
import groovy.json.JsonSlurper
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.TextProgressMonitor
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.kohsuke.github.GHEvent
import org.kohsuke.github.GHHook
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import spock.lang.Specification
import spock.lang.TempDir

import org.awaitility.Awaitility
import java.util.concurrent.TimeUnit

class WebhookIntegrationTests extends Specification {

    static String token = System.getenv('GH_TOKEN')
    static String repo = System.getenv('GH_REPO')

    static {
        assert token: "GH_TOKEN env variable is not set"
        assert repo: "GH_REPO env variable is not set"
    }

    String pushUrl = "https://${token}@github.com/${repo}.git"
    String cloneUrl = "https://github.com/${repo}.git"

    Network network = Network.newNetwork()

    GenericContainer gee =
            new GenericContainer<>(DockerImageName.parse("zobkov/github-events-emailer"))
                    .withNetwork(network)
                    .withExposedPorts(8080)
                    .withEnv('github.oauthToken', token)
                    .withEnv('smtp.recipients', 'team@corp.net')
                    .withEnv('smtp.server.host', 'mailhog')
                    .withEnv('smtp.server.port', '1025')

    GenericContainer mailhog =
            new GenericContainer<>(DockerImageName.parse("mailhog/mailhog"))
                    .withNetwork(network)
                    .withNetworkAliases('mailhog')
                    .withExposedPorts(8025, 1025)

    @TempDir
    File dirToCloneTo

    Git git

    String webhookUrl

    def setup() {
        git = cloneRepo()
        reset()
        push(true)
        mailhog.start()
        gee.start()

        String webhookUrl = openTunnel(gee.getMappedPort(8080))
        configureWebhook(webhookUrl)
    }

    def cleanup() {
        gee.stop()
        mailhog.stop()
        disconnectTunnel(webhookUrl)
    }

    def "Add files (text)"() {
        when:
        addTextFile()
        commit("Added a new text file")
        push()
        then:
        Awaitility.await().atMost(30, TimeUnit.SECONDS).until { getMessages()['count'] == 1 }

        with(getMessages()) {
            Map headers = it['items'][0]['Content']['Headers'].with { Map headers ->
                headers.collectEntries { String key, List value -> [key, value.first()] }
            }
            with(headers) {
                it['Content-Transfer-Encoding'] == '7bit'
                it['Content-Type'] == 'text/plain; charset=us-ascii'
                it['Subject'] =~ /New commit in repository ${repo}\/master - [0-9a-f]{5,40} Added a new text file/
                it['To'] == 'team@corp.net'
                it['X-Github-Delivery'] != null
                it['X-Github-Event'] == 'push'
                it['X-Github-Hook-Id'] != null
                it['X-Github-events-emailer-CommitId'] =~ /[0-9a-f]{5,40}/
                it['X-Github-events-emailer-Version'] != null
            }
            String body = it['items'][0]['Content']['Body']
            with(body.readLines()) {
                it.size() == 23
                it[0] =~ /URL:\s.*/
                it[1] =~ /SHA:\s.*/
                it[2] =~ /Author:\s.*/
                it[3] =~ /AuthorDate:\s.*/
                it[4] =~ /Commit:\s.*/
                it[5] =~ /CommitDate:\s.*/
                it[6] =~ /Parent commit\(s\):\s.*/
                it[7] == 'Message: Added a new text file'
                it[8] == '---'
                it[9] == 'Files:'
                it[10] == 'Added README'
                it[11] == '---'
                it[12] == 'diff --git a/README b/README'
                it[13] == 'new file mode 100644'
                it[14] == 'index 0000000..93d3190'
                it[15] == '--- /dev/null'
                it[16] == '+++ b/README'
                it[17] == '@@ -0,0 +1 @@'
                it[18] == '+My Groovy Project'
                it[19] == '\\ No newline at end of file'
                it[20] == ''
                it[21] == '---'
                it[22] =~ /X-GitHub-Delivery:\s.*/
            }
        }
    }

//    # Add files (binary)
//    echo "0000000: 504B 0506" | xxd -r - data.bin
//            git add data.bin
//    git commit -m "Added a new binary file"
//
//    # Change existing files (text)
//    echo 'My Java Project' > README
//            git add README
//    git commit -m "Modify the text file"
//
//    # Change existing text (binary)
//    echo "0000000: 377A BCAF" | xxd -r - data.bin
//            git add data.bin
//    git commit -m "Modify the binary file"
//
//    # Remove existing files
//    rm README data.bin
//            git add README data.bin
//            git commit -m "Delete files"
//
//    # Empty commit
//    git commit -m "Empty commit" --allow-empty
//
//    # Use commit message and a line in existing text file with non-ascii characters
//    echo '我的 Scala 项目 > README
//            git add README
//    git commit -m "Модификация текстового файла"

    Map<String, Object> getMessages() {
        RestTemplate restTemplate = new RestTemplate()
        int mailHogPort = mailhog.getMappedPort(8025)
        ResponseEntity<String> response
                = restTemplate.getForEntity("http://localhost:${mailHogPort}/api/v2/messages", String.class)
        Map<String, Object>.cast(new JsonSlurper().parseText(response.body))
    }

    NgrokClient ngrokClient = new NgrokClient.Builder().build()

    String openTunnel(int port) {
        CreateTunnel sshCreateTunnel = new CreateTunnel.Builder()
                .withProto(Proto.HTTP)
                .withAddr(port)
                .build()
        Tunnel sshTunnel = ngrokClient.connect(sshCreateTunnel)
        sshTunnel.publicUrl
    }

    boolean disconnectTunnel(String webhookUrl) {
        ngrokClient.disconnect(webhookUrl)
    }

    GHHook configureWebhook(String webhookUrl) {
        GitHub gh = new GitHubBuilder()
                .withOAuthToken(token)
                .build()
        gh.checkApiUrlValidity()
        GHRepository repository = gh.getRepository(repo)
        repository.getHooks().each { it.delete() }
        Collection<GHEvent> eventsToSend = [GHEvent.PUSH,]
        Map<String, String> settings = ['url'         : webhookUrl,
                                        'content_type': 'json',]
        repository.createHook(
                'web',
                settings,
                eventsToSend,
                true)
    }

    Git cloneRepo() {
        Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(dirToCloneTo)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider("${token}", ""))
                .setProgressMonitor(new TextProgressMonitor())
                .call()
    }

    DirCache addTextFile() {
        new File(dirToCloneTo, 'README').withPrintWriter {
            it.write('My Groovy Project')
        }
        git.add()
                .addFilepattern("README")
                .call()
    }

    RevCommit commit(String message) {
        git.commit()
                .setMessage(message)
                .call()
    }

    Iterable<PushResult> push(boolean force = false) {
        git.push()
                .setRemote(pushUrl)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider("${token}", ""))
                .setForce(force)
                .setProgressMonitor(new TextProgressMonitor())
                .call()
    }

    Ref reset() {
        Iterable<RevCommit> logs = git.log().call()
        String firstCommit = logs.toList().reverse().first().toObjectId().name()
        git.reset()
                .setMode(ResetCommand.ResetType.HARD)
                .setRef(firstCommit)
                .setProgressMonitor(new TextProgressMonitor())
                .call()
    }
}
