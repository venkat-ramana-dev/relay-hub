package dev.venkat.relayhub;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
public class RelayhubApplication {

	public static void main(String[] args) {

        SpringApplication.run(RelayhubApplication.class, args);
	}

}
