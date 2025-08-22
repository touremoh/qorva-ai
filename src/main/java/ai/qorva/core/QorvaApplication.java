package ai.qorva.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class QorvaApplication {
	public static void main(String[] args) {
		SpringApplication.run(QorvaApplication.class, args);
	}
}
