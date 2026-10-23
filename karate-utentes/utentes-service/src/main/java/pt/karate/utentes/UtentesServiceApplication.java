package pt.karate.utentes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UtentesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UtentesServiceApplication.class, args);
    }
}
