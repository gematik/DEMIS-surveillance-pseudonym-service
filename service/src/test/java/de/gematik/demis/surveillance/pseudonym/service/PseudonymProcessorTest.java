package de.gematik.demis.surveillance.pseudonym.service;

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

import de.gematik.demis.surveillance.pseudonym.api.PseudonymInput;
import de.gematik.demis.surveillance.pseudonym.api.PseudonymOutput;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PseudonymProcessorTest {

  private final PseudonymProcessor underTest = new PseudonymProcessor(null, null, null);

  @Test
  void createPseudonym() {
    final var input = new PseudonymInput("a", "b", LocalDate.of(2025, 7, 30));
    final var expected =
        PseudonymOutput.builder()
            .system("https://demis.rki.de/fhir/sid/SurveillancePatientPseudonym")
            .value("urn:uuid:10101010-1010-1010-1010-101010101010")
            .build();

    final PseudonymOutput result = underTest.createPseudonym(input);

    assertThat(result).isEqualTo(expected);
  }
}
