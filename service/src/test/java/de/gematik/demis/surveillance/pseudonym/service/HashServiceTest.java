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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;

@Slf4j
class HashServiceTest {

  private static final HexFormat HEX_FORMAT = HexFormat.of();
  private static final String PEPPER_SECRET = "MmbSvONGwV4J3WkG2Io5CrYTpSl2whWw9gjOodfVw2w=";

  private HashService underTest;

  private HashService createHashService(final String pepperSecret) {
    final HashService service = new HashService();
    service.setPepperSecret(pepperSecret);
    service.initPepperSecret();
    return service;
  }

  @Nested
  class PepperSecret {
    @Test
    void pepperMustNotBeEmpty() {
      assertThatThrownBy(() -> createHashService(""))
          .isInstanceOf(InvalidConfigurationPropertyValueException.class)
          .hasMessageContaining("sps.hash.pepper")
          .hasMessageContaining("Pepper secret must be set");
    }

    @Test
    void pepperMustBeBase64Encoded() {
      assertThatThrownBy(() -> createHashService("!!!not_base64!!!"))
          .isInstanceOf(InvalidConfigurationPropertyValueException.class)
          .hasMessageContaining("sps.hash.pepper")
          .hasMessageContaining("Illegal base64 character");
    }
  }

  @Nested
  class Hashing {

    @BeforeEach
    void setUp() {
      underTest = createHashService(PEPPER_SECRET);
    }

    @ParameterizedTest
    @CsvSource({
      "123e4567-e89b-12d3-a456-426614174000, aa47bc4669a4a3118526220f9f88737c5e327f206ab570a63f36f214ed973d52",
      "550e8400-e29b-41d4-a716-446655440000, f8c01affe442db74dcc2368fe9dca2c19b772eeb4fbc503c0208ff1c8ce34991",
      "f47ac10b-58cc-4372-a567-0e02b2c3d479, cdef39d617ed85465ea2100b525e566bfb3d0db9b228641c4da6f04ad67a438b"
    })
    void hashIsHMAC256(final String uuidAsInput, final String expectedHash) {
      final byte[] hash = underTest.hash(uuidAsInput);
      final String hashAsHexString = HEX_FORMAT.formatHex(hash);
      assertThat(hashAsHexString).isEqualTo(expectedHash);
    }

    @Test
    void hashIsDeterministic() {
      final String input = "testInput";
      final byte[] hash1 = underTest.hash(input);
      final byte[] hash2 = underTest.hash(input);

      assertThat(hash1).isEqualTo(hash2).isNotEmpty();
    }

    @Test
    void hashIsDifferentForDifferentInputs() {
      final byte[] hash1 = underTest.hash("input1");
      final byte[] hash2 = underTest.hash("input2");

      assertThat(hash1).isNotEqualTo(hash2).isNotEmpty();
    }

    @Test
    void hashDependsOnPepper() {
      final HashService serviceWithOtherPepper = createHashService("pepperB");

      final String input = "same Input";
      final byte[] hash1 = underTest.hash(input);
      final byte[] hash2 = serviceWithOtherPepper.hash(input);

      assertThat(hash1).isNotEqualTo(hash2).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "a",
          "123456",
          "langerTextMitSonderzeichen!@#$%^&*()",
          "input longer than 64 chars - verylonginputverylonginputverylonginputverylonginputverylonginputverylonginputverylonginputverylonginput"
        })
    void hashIsHexAnd64CharsLong(final String input) {
      final byte[] hash = underTest.hash(input);
      assertThat(hash).hasSize(32);
    }
  }
}
