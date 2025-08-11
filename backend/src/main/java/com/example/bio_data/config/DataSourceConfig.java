package com.example.bio_data.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;

@Configuration
@EnableTransactionManagement
public class DataSourceConfig {

    /**
     * 主数据源配置 (login数据库)
     */
    @Bean(name = "loginDataSource")
    @Primary
    @ConfigurationProperties(prefix = "datasource.login")
    public DataSource loginDataSource() {
        return new HikariDataSource();
    }

    /**
     * 主数据源的JdbcTemplate
     */
    @Bean(name = "loginJdbcTemplate")
    @Primary
    public JdbcTemplate loginJdbcTemplate() {
        return new JdbcTemplate(loginDataSource());
    }

    /**
     * 主EntityManagerFactory配置
     */
    @Bean(name = "entityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(loginDataSource())
                .packages("com.example.bio_data") // 扫描实体类的包
                .persistenceUnit("primary")
                .build();
    }

    /**
     * 主TransactionManager配置
     */
    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager primaryTransactionManager(
            @org.springframework.beans.factory.annotation.Qualifier("entityManagerFactory") 
            EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
} 