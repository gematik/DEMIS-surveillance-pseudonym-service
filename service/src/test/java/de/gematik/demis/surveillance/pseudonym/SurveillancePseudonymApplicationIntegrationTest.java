package de.gematik.demis.surveillance.pseudonym;

/*-
 * #%L
 * surveillance-pseudonym-service
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

import de.gematik.demis.surveillance.pseudonym.repository.ChainRepository;
import de.gematik.demis.surveillance.pseudonym.repository.PeriodRepository;
import de.gematik.demis.surveillance.pseudonym.repository.PseudonymChainRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
    })
class SurveillancePseudonymApplicationIntegrationTest {
  @MockitoBean PseudonymChainRepository pseudonymChainRepository;
  @MockitoBean PeriodRepository periodRepositoryMock;
  @MockitoBean ChainRepository chainRepositoryMock;
  @MockitoBean PlatformTransactionManager platformTransactionManagerMock;

  @Autowired private ApplicationContext applicationContext;

  @Test
  void contextLoads() {
    assertThat(applicationContext).isNotNull();
  }
}
