package com.giorgi.gymcrm;

import com.giorgi.gymcrm.config.RootConfig;
import com.giorgi.gymcrm.config.WebConfig;
import com.giorgi.gymcrm.web.RequestLoggingFilter;
import com.giorgi.gymcrm.web.TransactionIdFilter;
import jakarta.servlet.Filter;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
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
        context.setParentClassLoader(GymCrmApplication.class.getClassLoader());
        addFilter(context, "transactionIdFilter", new TransactionIdFilter());
        addFilter(context, "requestLoggingFilter", new RequestLoggingFilter());

        Wrapper dispatcher = Tomcat.addServlet(context, DISPATCHER, new DispatcherServlet(webContext));
        dispatcher.setLoadOnStartup(1);
        context.addServletMappingDecoded("/", DISPATCHER);

        tomcat.start();
        tomcat.getServer().await();
    }

    private static void addFilter(Context context, String name, Filter filter) {
        FilterDef definition = new FilterDef();
        definition.setFilterName(name);
        definition.setFilter(filter);
        context.addFilterDef(definition);

        FilterMap mapping = new FilterMap();
        mapping.setFilterName(name);
        mapping.addURLPattern("/*");
        context.addFilterMap(mapping);
    }
}