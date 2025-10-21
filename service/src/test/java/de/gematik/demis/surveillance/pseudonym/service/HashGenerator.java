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

import com.github.dockerjava.zerodep.shaded.org.apache.commons.codec.binary.Hex;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HashGenerator {
  public static void main(final String[] args) {
    final String pepperBase64Encoded = "0WAoZ+nfpzBJrducAjGm0oBTnFB+jFfxwA/dcFGhsqA=";
    final String[] inputs =
        new String[] {"CL-PSEUDO-1", "CL-PSEUDO-2", "OTHER-CHAIN-1", "OTHER-CHAIN-2"};

    final HashService service = new HashService();
    service.setPepperSecret(pepperBase64Encoded);
    service.initPepperSecret();

    for (final String s : inputs) {
      final byte[] hash = service.hash(s);
      final String hashHex = Hex.encodeHexString(hash);
      log.info("{} -> {}", s, hashHex);
    }
  }
}
