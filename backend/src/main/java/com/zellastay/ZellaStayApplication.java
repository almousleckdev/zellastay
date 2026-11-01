package com.zellastay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@Modulithic(systemName = "Zella Stay")
public class ZellaStayApplication {

  public static void main(String[] args) {
    SpringApplication.run(ZellaStayApplication.class, args);
  }
}
