package com.smartseason.notification.platform;

import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Configuration
public class ReadReplicaConfig {

    private static final Logger log = LoggerFactory.getLogger(ReadReplicaConfig.class);

    private enum Route { PRIMARY, REPLICA }

    private static final ThreadLocal<Boolean> FORCE_PRIMARY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static <T> T onPrimary(Supplier<T> work) {
        FORCE_PRIMARY.set(Boolean.TRUE);
        try {
            return work.get();
        } finally {

            FORCE_PRIMARY.remove();
        }
    }

    @Bean
    @Primary
    public DataSource dataSource(
            DataSourceProperties properties,
            @Value("${smartseason.datasource.replica-host:}") String replicaHost,
            @Value("${DB_POOL_MAX:10}") int poolMax) {

        HikariDataSource primary = build(properties, properties.getUrl(), poolMax, "primary");

        if (!StringUtils.hasText(replicaHost)) {
            log.info("No read replica configured; all reads go to the primary.");
            return primary;
        }

        String replicaUrl = withHost(properties.getUrl(), replicaHost);
        if (replicaUrl == null) {

            log.warn("Could not derive a replica URL from '{}'; staying on the primary.",
                    properties.getUrl());
            return primary;
        }

        HikariDataSource replica = build(properties, replicaUrl, poolMax, "replica");

        Map<Object, Object> routes = new HashMap<>();
        routes.put(Route.PRIMARY, primary);
        routes.put(Route.REPLICA, replica);

        AbstractRoutingDataSource router = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                if (Boolean.TRUE.equals(FORCE_PRIMARY.get())) {
                    return Route.PRIMARY;
                }
                return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                        ? Route.REPLICA
                        : Route.PRIMARY;
            }
        };
        router.setTargetDataSources(routes);
        router.setDefaultTargetDataSource(primary);
        router.afterPropertiesSet();

        log.info("Read replica configured; read-only transactions will use it.");
        return router;
    }

    static String withHost(String jdbcUrl, String replicaHost) {
        if (jdbcUrl == null) {
            return null;
        }
        Matcher m = JDBC_HOST.matcher(jdbcUrl);
        if (!m.find()) {
            return null;
        }

        return m.group(1) + replicaHost + m.group(3);
    }

    private static final Pattern JDBC_HOST =
            Pattern.compile("^(jdbc:postgresql://)([^/:?]+)(.*)$");

    private HikariDataSource build(DataSourceProperties properties, String url, int poolMax, String name) {
        HikariDataSource ds = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .url(url)
                .build();
        ds.setPoolName("notification-" + name);

        ds.setMaximumPoolSize(poolMax);
        ds.setMinimumIdle(2);
        return ds;
    }
}
