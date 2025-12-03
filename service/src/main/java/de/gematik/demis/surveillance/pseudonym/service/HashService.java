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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.AccessLevel;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.stereotype.Service;

/**
 * Service for generating cryptographic hashes from patient pseudonyms using HMAC-SHA256.
 *
 * <p>This service is responsible to transform client-side generated pseudonyms into
 * database-storable hashes, ensuring that <strong>no original pseudonyms are ever
 * persisted</strong> - only their cryptographic hash representations.
 *
 * <p><strong>Security Considerations:</strong>
 *
 * <ul>
 *   <li>Uses a configurable pepper value to ensure application-specific hash isolation
 *   <li>The pepper ensures that different applications generate different hashes for identical
 *       pseudonyms
 *   <li>The pepper configuration parameter is immutable and must never be changed in production
 *   <li>Changing the pepper would invalidate all existing hashes and break pseudonym chain
 *       consistency
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This service is thread-safe and can be used concurrently.
 */
@Service
class HashService {

  private static final String HMAC_SHA256 = "HmacSHA256";

  @Value("${sps.hash.pepper}")
  @Setter(AccessLevel.PACKAGE)
  private String pepperSecret;

  private SecretKeySpec secretKeySpec;

  @PostConstruct
  void initPepperSecret() {
    secretKeySpec = new SecretKeySpec(getPepperSecret(), HMAC_SHA256);
    // check that MAC Algo can be used
    createMac();
  }

  private byte[] getPepperSecret() {
    if (pepperSecret == null || pepperSecret.isBlank()) {
      throw new InvalidConfigurationPropertyValueException(
          "sps.hash.pepper", "", "Pepper secret must be set");
    }
    try {
      return Base64.getDecoder().decode(pepperSecret);
    } catch (final Exception e) {
      throw new InvalidConfigurationPropertyValueException(
          "sps.hash.pepper", "***", "Pepper secret must be Base64 encoded " + e);
    }
  }

  private Mac createMac() {
    try {
      final Mac mac = Mac.getInstance(HMAC_SHA256);
      mac.init(secretKeySpec);
      return mac;
    } catch (final Exception e) {
      // could never happen
      throw new IllegalStateException("Error initializing HMAC", e);
    }
  }

  /**
   * Generates a cryptographic hash from the provided pseudonym using HMAC-SHA256.
   *
   * <p><strong>Hash Properties:</strong>
   *
   * <ul>
   *   <li><strong>Deterministic:</strong> Identical pseudonyms always generate identical hashes
   *   <li><strong>Collision-resistant:</strong> Different pseudonyms produce different hashes
   *   <li><strong>One-way:</strong> Original pseudonym cannot be derived from the hash
   *   <li><strong>Avalanche effect:</strong> Small changes in input produce drastically different
   *       output
   * </ul>
   *
   * @param input the patient pseudonym to be hashed (must not be null or empty)
   * @return the HMAC-SHA256 hash as byte array
   */
  public byte[] hash(final String input) {
    // Note: Mac is not thread-safe
    final Mac mac = createMac();
    return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
  }
}
