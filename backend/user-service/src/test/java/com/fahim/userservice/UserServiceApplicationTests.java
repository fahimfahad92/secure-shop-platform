package com.fahim.userservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

@SpringBootTest
class UserServiceApplicationTests extends AbstractIntegrationTest {

    @Autowired private ApplicationContext context;

    @Autowired private DataSource dataSource;

    @Test
    void contextLoads() {}

    /**
     * Finding #4: no Open Session in View, so a request holds a connection only while a repository
     * call (or a transaction) runs, not until the response is written.
     */
    @Test
    void openInViewIsOff() {
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
    }

    /** Finding #29: an explicit pool instead of Hikari's defaults. Values: docs/development.md. */
    @Test
    void poolIsConfigured() throws SQLException {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);

        assertThat(pool.getPoolName()).isEqualTo("user-service-pool");
        assertThat(pool.getMaximumPoolSize()).isEqualTo(5);
        assertThat(pool.getMinimumIdle()).isEqualTo(2);
        assertThat(pool.getConnectionTimeout()).isEqualTo(3000);
        assertThat(pool.getLeakDetectionThreshold()).isEqualTo(2000);
    }
}
