package de.gematik.demis.surveillance.pseudonym.purger.test;

/*-
 * #%L
 * surveillance-pseudonym-purger
 * %%
 * Copyright (C) 2025 gematik GmbH
 * %%
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import net.ttddyy.dsproxy.listener.MethodExecutionContext;
import net.ttddyy.dsproxy.listener.MethodExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;

@Slf4j
public class TxCounter implements BeanPostProcessor {
  private final AtomicInteger commits = new AtomicInteger();
  private final AtomicInteger rollbacks = new AtomicInteger();

  @Override
  public Object postProcessAfterInitialization(final Object bean, final String beanName) {
    if (bean instanceof DataSource dataSource) {
      log.info("proxy datasource {}", beanName);
      return proxyDataSource(dataSource);
    }
    return bean;
  }

  public void reset() {
    commits.set(0);
    rollbacks.set(0);
  }

  public int getCommits() {
    return commits.get();
  }

  public int getRollbacks() {
    return rollbacks.get();
  }

  private DataSource proxyDataSource(final DataSource original) {
    final MethodExecutionListener methodListener =
        new MethodExecutionListener() {
          @Override
          public void beforeMethod(final MethodExecutionContext ctx) {
            // nothing to do here
          }

          @Override
          public void afterMethod(final MethodExecutionContext ctx) {
            final String name = ctx.getMethod().getName();
            if ("commit".equals(name)) {
              commits.incrementAndGet();
            } else if ("rollback".equals(name)) {
              rollbacks.incrementAndGet();
            }
          }
        };

    return ProxyDataSourceBuilder.create(original)
        .name("proxy-ds")
        .methodListener(methodListener)
        .build();
  }
}
