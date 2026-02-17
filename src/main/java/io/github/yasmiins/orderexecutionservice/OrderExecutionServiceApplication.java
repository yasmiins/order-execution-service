package io.github.yasmiins.orderexecutionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrderExecutionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderExecutionServiceApplication.class, args);
    }

}
