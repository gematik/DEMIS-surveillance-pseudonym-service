package de.gematik.demis.surveillance.pseudonym.purger;

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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootTest(
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
    })
@Slf4j
class PurgerApplicationIntegrationTest {
  @Autowired private ApplicationContext applicationContext;
  @Autowired private EntityManager entityManager;

  @Test
  void contextLoads() {
    // Note the call order: SpringBootTest starts the whole application context before calling this
    // test method
    // That means the command line runner is also already executed and completed.
    log.info("Purger Job was executed successfully");
    assertThat(applicationContext).isNotNull();
    // verify that the purge action was called, which executes at least 3 native sql statements
    verify(entityManager, atLeast(3)).createNativeQuery(anyString());
  }

  @TestConfiguration
  static class MockEntityManager {
    @Bean
    EntityManager entityManager() {
      final EntityManager entityManager = mock(EntityManager.class);
      final Query query = mock(Query.class);
      when(entityManager.createNativeQuery(anyString())).thenReturn(query);
      return entityManager;
    }
  }
}
