package com.archive.common;

import com.archive.service.FailureLogService;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 业务 Service AOP — 异常兜底写入 failure_log (无 AspectJ 依赖).
 *
 * @author Mavis
 */
@Configuration
@RequiredArgsConstructor
public class BusinessAop {

    private final FailureLogService failureLogService;

    @Bean
    public Advisor serviceFailureLogAdvisor() {
        AnnotationMatchingPointcut pointcut = new AnnotationMatchingPointcut(Service.class, true);
        MethodInterceptor interceptor = invocation -> {
            if (invocation.getThis() instanceof FailureLogService) {
                return invocation.proceed();
            }
            try {
                return invocation.proceed();
            } catch (Throwable t) {
                String path = invocation.getMethod().getDeclaringClass().getName()
                        + "." + invocation.getMethod().getName();
                failureLogService.log(path, t.getClass().getSimpleName(), t.getMessage(), stackTraceToString(t));
                throw t;
            }
        };
        return new DefaultPointcutAdvisor(pointcut, interceptor);
    }

    private static String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String trace = sw.toString();
        return trace.length() > 8000 ? trace.substring(0, 8000) : trace;
    }
}
