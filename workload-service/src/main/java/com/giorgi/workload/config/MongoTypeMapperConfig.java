package com.giorgi.workload.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

// No document here is polymorphic, so Boot's converter writes no _class type key.
@Configuration(proxyBeanMethods = false)
public class MongoTypeMapperConfig {

    public MongoTypeMapperConfig(MappingMongoConverter converter) {
        converter.setTypeMapper(new DefaultMongoTypeMapper(null, converter.getMappingContext()));
    }
}
