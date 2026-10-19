package org.tanshin.voiceprint.cp.abe;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableAsync;

@Push // 1. 添加这个注解，开启异步推送功能
@EnableAsync
//@UIScope
@Theme(variant = Lumo.DARK)
@SpringBootApplication
public class App extends SpringBootServletInitializer implements AppShellConfigurator {  // 2. 实现这个接口

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
        System.out.println("http://localhost:8080/");
        System.out.println("http://localhost:8080/h2-console");
    }

}