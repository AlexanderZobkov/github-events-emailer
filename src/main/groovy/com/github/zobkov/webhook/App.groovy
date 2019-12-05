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

    /**
     * A main method to start this application.
     */
    static void main(String[] args) {
        SpringApplication.run(App, args)
    }

}
