package com.ori_leibo.nbafantasyscout;

import org.springframework.boot.SpringApplication;

public class TestNbaFantasyScoutApplication {

    public static void main(String[] args) {
        SpringApplication.from(NbaFantasyScoutApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
