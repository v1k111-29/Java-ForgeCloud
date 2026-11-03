package com.mockforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Spring starts the web server and connects the application classes from here.
@SpringBootApplication
public class MockforgeCloudApplication {

    public static void main(String[] arguments) {

        SpringApplication.run(MockforgeCloudApplication.class, arguments);
    }
}
