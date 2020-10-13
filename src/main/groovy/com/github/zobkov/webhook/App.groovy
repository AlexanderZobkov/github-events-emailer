package com.github.zobkov.webhook

import groovy.transform.CompileStatic
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

/**
*  Webhook app main class.
*/
@CompileStatic
@SpringBootApplication
class App {

    static {
        // Redirects JUL to Log4J
        System.properties.setProperty('java.util.logging.manager', 'org.apache.logging.log4j.jul.LogManager')
    }

    /**
     * A main method to start this application.
     */
    static void main(String[] args) {
        SpringApplication.run(App, args)
    }

}
