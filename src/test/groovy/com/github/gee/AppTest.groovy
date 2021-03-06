package com.github.gee

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.support.TestPropertySourceUtils
import org.springframework.util.SocketUtils

@SpringBootTest(
        properties = ['github.offline=true', 'smtp.server.host=smtp.host']
)
@ContextConfiguration(initializers = RandomPortInitializer.class)
class AppTest {

    @Test
    void appStarts() {

    }

    static class RandomPortInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        void initialize(ConfigurableApplicationContext applicationContext) {
            int randomPort = SocketUtils.findAvailableTcpPort()
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext,
                    "webhook.listen.port=" + randomPort)
        }

    }

}
