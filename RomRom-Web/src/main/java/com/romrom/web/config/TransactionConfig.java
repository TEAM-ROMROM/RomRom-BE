package com.romrom.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.transaction.ChainedTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TransactionConfig {

    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    @Bean(name = "chainedTransactionManager")
    public ChainedTransactionManager transactionManager(
        PlatformTransactionManager transactionManager,      // 스프링 부트의 자동 설정이기 때문에, 이름 변경 X
        MongoTransactionManager mongoTransactionManager) {
        return new ChainedTransactionManager(transactionManager, mongoTransactionManager);
    }
}