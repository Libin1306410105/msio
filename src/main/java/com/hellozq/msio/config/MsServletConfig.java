package com.hellozq.msio.config;

import com.hellozq.msio.bean.common.MsIoServlet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * 具体为上传的文件使用的接口的处理方式，定义新的servlet
 */
@Configuration
@ConditionalOnExpression("${spring.msIo.autoServlet:true}")
public class MsServletConfig {

    private static final Log log = LogFactory.getLog(MsServletConfig.class);

    @Value("${spring.micro.listen.url:/upload/*}")
    private String listenerUrl;

    @Bean
    public MsIoServlet msIOServlet(){
        return new MsIoServlet();
    }


    @Bean
    public ServletRegistrationBean<DispatcherServlet> restServlet(){
        log.info("-------------执行自定义跳转方式的servlet中---------------");
        //注册新的servlet用于监听上传文件的接口
        return new ServletRegistrationBean<>(msIOServlet(),listenerUrl.split(","));
    }
}
