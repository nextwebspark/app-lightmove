package app.lightmove.api.core.tenant;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Puts {@link TenantAwareDataSource} in front of Boot's pool, so every consumer of the DataSource borrows through it. */
@Configuration(proxyBeanMethods = false)
public class TenantDataSourceConfig {

    @Bean
    static BeanPostProcessor tenantAwareDataSource() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof DataSource pool && !(bean instanceof TenantAwareDataSource)
                        ? new TenantAwareDataSource(pool)
                        : bean;
            }
        };
    }
}
