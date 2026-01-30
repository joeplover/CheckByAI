package com.checkai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class CheckAiApplication {

    public static void main(String[] args) {
        // 设置系统属性以避免Hibernate元数据初始化问题
        System.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        System.setProperty("spring.jpa.properties.hibernate.temp.use_jdbc_metadata_defaults", "false");
        
        SpringApplication.run(CheckAiApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
