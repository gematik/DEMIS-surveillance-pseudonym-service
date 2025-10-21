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

import de.gematik.demis.surveillance.pseudonym.api.PseudonymInput;
import de.gematik.demis.surveillance.pseudonym.api.PseudonymOutput;
import de.gematik.demis.surveillance.pseudonym.service.ChainService.ChainResult;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

/**
 * Main Service for processing pseudonym requests
 *
 * <p>For details see <a href="https://wiki.gematik.de/x/kCvAHw">specification</a>
 */
@Service
@RequiredArgsConstructor
public class PseudonymProcessor {

  private static final String CODE_SYSTEM =
      "https://demis.rki.de/fhir/sid/SurveillancePatientPseudonym";

  private static final String UUID_PREFIX = "urn:uuid:";

  private final HashService hashService;
  private final ChainService chainService;
  private final PeriodService periodService;

  @Value("${feature.flag.individual_pseudonym}")
  @Setter(AccessLevel.PACKAGE)
  private boolean individualPseudonym;

  /**
   * At the moment the input is ignored and a fix pseudonym is returned. This will be changed soon
   * (next user story)
   */
  public PseudonymOutput createPseudonym(final PseudonymInput input) {
    final UUID newPseudonym;
    if (individualPseudonym) {
      final ChainResult chainResult = chainService.chain(hashPseudonyms(input));
      newPseudonym = periodService.period(chainResult, input.date());
    } else {
      newPseudonym = generatePseudonym();
    }

    return toOutput(newPseudonym);
  }

  private Pair<byte[], byte[]> hashPseudonyms(final PseudonymInput pseudonymInput) {
    return Pair.of(
        hashService.hash(pseudonymInput.pseudonym1()),
        hashService.hash(pseudonymInput.pseudonym2()));
  }

  private PseudonymOutput toOutput(final UUID pseudonym) {
    final String value = UUID_PREFIX + pseudonym;
    return PseudonymOutput.builder().system(CODE_SYSTEM).value(value).build();
  }

  private UUID generatePseudonym() {
    return UUID.fromString("10101010-1010-1010-1010-101010101010");
  }
}
