package com.github.gee

import groovy.transform.CompileStatic
import org.slf4j.bridge.SLF4JBridgeHandler
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

import java.util.logging.LogManager

/**
*  The app main class.
*/
@CompileStatic
@SpringBootApplication
class App {

    static {
        // Redirects JUL to Sl4j
        LogManager.getLogManager().reset()
        SLF4JBridgeHandler.install()
    }

    /**
     * A main method to start this application.
     */
    static void main(String[] args) {
        SpringApplication.run(App, args)
    }

}
