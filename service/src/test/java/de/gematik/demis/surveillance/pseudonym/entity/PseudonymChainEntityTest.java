package de.gematik.demis.surveillance.pseudonym.entity;

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

import org.junit.jupiter.api.Test;

class PseudonymChainEntityTest {

  @Test
  void newCreatedEntityIsNew() {
    final PseudonymChainEntity underTest = new PseudonymChainEntity();
    assertThat(underTest.isNew()).isTrue();
  }

  @Test
  void getIdReturnsPseudonym() {
    final PseudonymChainEntity underTest = new PseudonymChainEntity();
    final byte[] pseudonym = new byte[] {1, 2, 3, 4};
    underTest.setPseudonym(pseudonym);
    assertThat(underTest.getId()).isEqualTo(pseudonym);
  }
}
