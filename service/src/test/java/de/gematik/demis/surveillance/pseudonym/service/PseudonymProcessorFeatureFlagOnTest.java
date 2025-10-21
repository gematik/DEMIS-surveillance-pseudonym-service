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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.surveillance.pseudonym.api.PseudonymInput;
import de.gematik.demis.surveillance.pseudonym.api.PseudonymOutput;
import de.gematik.demis.surveillance.pseudonym.service.ChainService.ChainResult;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Pair;

@ExtendWith(MockitoExtension.class)
class PseudonymProcessorFeatureFlagOnTest {

  @Mock HashService hashServiceMock;
  @Mock ChainService chainServiceMock;
  @Mock PeriodService periodServiceMock;
  @InjectMocks PseudonymProcessor underTest;

  @Captor ArgumentCaptor<String> hashArgumentCaptor;
  @Captor ArgumentCaptor<Pair<byte[], byte[]>> chainArgumentCaptor;
  @Captor ArgumentCaptor<ChainResult> periodFirstArgumentCaptor;
  @Captor ArgumentCaptor<LocalDate> periodSecondArgumentCaptor;

  @BeforeEach
  void activateFeatureFlag() {
    underTest.setIndividualPseudonym(true);
  }

  @Test
  void createPseudonym() {
    final var input = new PseudonymInput("a", "b", LocalDate.of(2025, 7, 30));
    final byte[] hash1 = new byte[] {1, 2, 3};
    final byte[] hash2 = new byte[] {4, 5, 6};
    final ChainResult chainResult = new ChainResult(UUID.randomUUID(), false);
    final UUID periodUuid = UUID.randomUUID();
    final PseudonymOutput expected = toOutput(periodUuid.toString());

    when(hashServiceMock.hash(anyString())).thenReturn(hash1).thenReturn(hash2);
    when(chainServiceMock.chain(any())).thenReturn(chainResult);
    when(periodServiceMock.period(any(), any())).thenReturn(periodUuid);

    final PseudonymOutput result = underTest.createPseudonym(input);

    assertThat(result).isEqualTo(expected);

    verify(hashServiceMock, times(2)).hash(hashArgumentCaptor.capture());
    assertThat(hashArgumentCaptor.getAllValues())
        .containsExactly(input.pseudonym1(), input.pseudonym2());

    verify(chainServiceMock).chain(chainArgumentCaptor.capture());
    assertThat(chainArgumentCaptor.getValue()).isEqualTo(Pair.of(hash1, hash2));

    verify(periodServiceMock)
        .period(periodFirstArgumentCaptor.capture(), periodSecondArgumentCaptor.capture());
    assertThat(periodFirstArgumentCaptor.getValue()).isEqualTo(chainResult);
    assertThat(periodSecondArgumentCaptor.getValue()).isEqualTo(input.date());
  }

  private PseudonymOutput toOutput(final String newPseudonym) {
    return PseudonymOutput.builder()
        .system("https://demis.rki.de/fhir/sid/SurveillancePatientPseudonym")
        .value("urn:uuid:" + newPseudonym)
        .build();
  }
}
