package com.aiplatform;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class AiPlatformApplication {

	public static void main(String[] args) {
		// Load .env variables into System Properties for Spring to find
		Dotenv dotenv = Dotenv.configure()
				.directory("./")
				.ignoreIfMissing()
				.load();
		
		dotenv.entries().forEach(entry -> {
			String val = entry.getValue() != null ? entry.getValue().trim() : "";
			System.setProperty(entry.getKey(), val);
		});

		SpringApplication.run(AiPlatformApplication.class, args);
	}

}
