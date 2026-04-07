package com.teadrink;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.teadrink.mapper")
public class TeadrinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeadrinkApplication.class, args);
    }
}
