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
//import java.util.HashMap;
//import java.util.Map;

@Configuration
@EnableTransactionManagement
public class DataSourceConfig {

    /**
     * 主数据源配置
     */
    @Bean(name = "primaryDataSource")
    @Primary
    @ConfigurationProperties(prefix = "datasource.primary")
    public DataSource primaryDataSource() {
        return new HikariDataSource();
    }

    /**
     * 第二个数据源配置
     */
    @Bean(name = "secondaryDataSource")
    @ConfigurationProperties(prefix = "datasource.secondary")
    public DataSource secondaryDataSource() {
        return new HikariDataSource();
    }

    /**
     * 第三个数据源配置 (login数据库)
     */
    @Bean(name = "loginDataSource")
    @ConfigurationProperties(prefix = "datasource.login")
    public DataSource loginDataSource() {
        return new HikariDataSource();
    }

    /**
     * 主数据源的JdbcTemplate
     */
    @Bean(name = "primaryJdbcTemplate")
    @Primary
    public JdbcTemplate primaryJdbcTemplate() {
        return new JdbcTemplate(primaryDataSource());
    }

    /**
     * 第二个数据源的JdbcTemplate
     */
    @Bean(name = "secondaryJdbcTemplate")
    public JdbcTemplate secondaryJdbcTemplate() {
        return new JdbcTemplate(secondaryDataSource());
    }

    /**
     * 第三个数据源的JdbcTemplate (login数据库)
     */
    @Bean(name = "loginJdbcTemplate")
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
                .dataSource(primaryDataSource())
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