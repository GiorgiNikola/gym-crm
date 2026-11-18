package com.giorgi.gymcrm.workload;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;

@Configuration
public class WorkloadClientConfig {

    @Bean(autowireCandidate = false)
    @LoadBalanced
    public RestClient.Builder workloadRestClientBuilder(WorkloadProperties properties) {
        return restClientBuilder(properties);
    }

    @Bean
    public WorkloadClient workloadClient(WorkloadProperties properties, ServiceTokenProvider tokens) {
        return createClient(workloadRestClientBuilder(properties), tokens);
    }

    static RestClient.Builder restClientBuilder(WorkloadProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory);
    }

    static WorkloadClient createClient(RestClient.Builder builder, ServiceTokenProvider tokens) {
        RestClient restClient = builder
                .requestInterceptor(new ServiceTokenInterceptor(tokens))
                .requestInterceptor(new TransactionIdInterceptor())
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(WorkloadClient.class);
    }
}
