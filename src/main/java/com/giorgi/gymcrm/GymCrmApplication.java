package com.giorgi.gymcrm;

import com.giorgi.gymcrm.config.RootConfig;
import com.giorgi.gymcrm.config.WebConfig;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.File;

public class GymCrmApplication {

    private static final int PORT = 8080;
    private static final String DISPATCHER = "dispatcher";

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext rootContext = new AnnotationConfigApplicationContext(RootConfig.class);
        rootContext.registerShutdownHook();

        AnnotationConfigWebApplicationContext webContext = new AnnotationConfigWebApplicationContext();
        webContext.register(WebConfig.class);
        webContext.setParent(rootContext);

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(PORT);
        tomcat.setBaseDir(new File(System.getProperty("java.io.tmpdir"), "gym-crm-tomcat").getAbsolutePath());
        tomcat.getConnector();

        Context context = tomcat.addContext("", null);
        Wrapper dispatcher = Tomcat.addServlet(context, DISPATCHER, new DispatcherServlet(webContext));
        dispatcher.setLoadOnStartup(1);
        context.addServletMappingDecoded("/", DISPATCHER);

        tomcat.start();
        tomcat.getServer().await();
    }
}