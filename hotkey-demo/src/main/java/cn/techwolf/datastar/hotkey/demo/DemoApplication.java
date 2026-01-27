package cn.techwolf.datastar.hotkey.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo启动类
 * 端口：8080
 * Redis地址：127.0.0.1:6379
 *
 * @author techwolf
 * @date 2024
 */
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
