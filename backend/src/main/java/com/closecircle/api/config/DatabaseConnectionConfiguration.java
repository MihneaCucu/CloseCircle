package com.closecircle.api.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConnectionConfiguration {
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password) {
        var connection = PostgresConnectionSettings.from(url, username, password);
        var dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(connection.jdbcUrl());
        dataSource.setUsername(connection.username());
        dataSource.setPassword(connection.password());
        return dataSource;
    }

    @Bean
    FlywayConfigurationCustomizer flywayConnectionCustomizer(
            @Value("${FLYWAY_DATABASE_URL:${DATABASE_URL_UNPOOLED:${spring.datasource.url}}}") String url,
            @Value("${FLYWAY_DATABASE_USERNAME:${DATABASE_USERNAME:}}") String username,
            @Value("${FLYWAY_DATABASE_PASSWORD:${DATABASE_PASSWORD:}}") String password) {
        var connection = PostgresConnectionSettings.from(url, username, password);
        return configuration -> configuration.dataSource(
                connection.jdbcUrl(), connection.username(), connection.password());
    }

    private record PostgresConnectionSettings(String jdbcUrl, String username, String password) {
        static PostgresConnectionSettings from(String url, String username, String password) {
            String resolvedUsername = username;
            String resolvedPassword = password;

            if (url.startsWith("jdbc:postgresql:")) {
                return new PostgresConnectionSettings(url, resolvedUsername, resolvedPassword);
            }
            if (!url.startsWith("postgresql://") && !url.startsWith("postgres://")) {
                throw new IllegalArgumentException("Database URL must use PostgreSQL JDBC or URI format");
            }

            URI uri = URI.create(url);
            String authority = uri.getRawAuthority();
            if (authority == null || uri.getHost() == null) {
                throw new IllegalArgumentException("Database URL must include a PostgreSQL host");
            }

            String hostAndPort = authority.substring(authority.lastIndexOf('@') + 1);
            String jdbcUrl = "jdbc:postgresql://" + hostAndPort + uri.getRawPath()
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());

            String rawUserInfo = uri.getRawUserInfo();
            if (rawUserInfo != null) {
                int separator = rawUserInfo.indexOf(':');
                String urlUsername = separator < 0 ? rawUserInfo : rawUserInfo.substring(0, separator);
                String urlPassword = separator < 0 ? "" : rawUserInfo.substring(separator + 1);
                if (resolvedUsername.isBlank()) resolvedUsername = decodeUserInfo(urlUsername);
                if (resolvedPassword.isBlank()) resolvedPassword = decodeUserInfo(urlPassword);
            }

            if (resolvedUsername.isBlank()) resolvedUsername = "closecircle";
            if (resolvedPassword == null) resolvedPassword = "";
            return new PostgresConnectionSettings(jdbcUrl, resolvedUsername, resolvedPassword);
        }

        private static String decodeUserInfo(String value) {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        }
    }
}
