package br.com.camplana;

import org.springframework.boot.SpringApplication;

public class TestCamplanaApplication {

	public static void main(String[] args) {
		SpringApplication.from(CamplanaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
