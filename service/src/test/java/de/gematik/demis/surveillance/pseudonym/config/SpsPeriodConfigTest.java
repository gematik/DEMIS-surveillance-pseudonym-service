package de.gematik.demis.surveillance.pseudonym.config;

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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Month;
import java.time.MonthDay;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SpsPeriodConfigTest {

  private static final String[] VALID_PROPERTIES = {
    "sps.period.max-lifetime-in-years=5", "sps.period.adjust-reference-day=--07-01"
  };

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(ConfigPropertiesTestConfiguration.class);

  @Test
  void validProperties() {
    contextRunner
        .withPropertyValues(VALID_PROPERTIES)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              final SpsPeriodConfig config = context.getBean(SpsPeriodConfig.class);

              assertThat(config).isNotNull();
              assertThat(config.maxLifetimeInYears()).isEqualTo(5);
              assertThat(config.adjustReferenceDay()).isEqualTo(MonthDay.of(Month.JULY, 1));
            });
  }

  @TestConfiguration
  @EnableConfigurationProperties(SpsPeriodConfig.class)
  static class ConfigPropertiesTestConfiguration {}
}
