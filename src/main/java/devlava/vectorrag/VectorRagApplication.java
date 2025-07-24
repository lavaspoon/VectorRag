package devlava.vectorrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VectorRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(VectorRagApplication.class, args);
    }

}
