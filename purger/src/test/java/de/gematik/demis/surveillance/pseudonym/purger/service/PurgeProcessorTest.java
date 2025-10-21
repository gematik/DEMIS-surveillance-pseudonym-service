package de.gematik.demis.surveillance.pseudonym.purger.service;

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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.surveillance.pseudonym.purger.config.SpsPurgerConfigProps;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurgeProcessorTest {
  private static final int BATCH_SIZE = 10;
  private static final int RETENTION_YEARS = 5;
  private static final int CURRENT_YEAR = LocalDate.now().getYear();

  @Mock DeleteService deleteService;

  private PurgeProcessor underTest;

  @BeforeEach
  void setUp() {
    final var configProps =
        SpsPurgerConfigProps.builder()
            .batchSize(BATCH_SIZE)
            .retentionYears(RETENTION_YEARS)
            .build();
    underTest = new PurgeProcessor(configProps, deleteService);
  }

  @Test
  void lastYearOfRetentionIsCorrect() {
    underTest.purgeDatabase();

    final var yearCapture = ArgumentCaptor.forClass(Integer.class);
    verify(deleteService).deletePeriodsOlderThan(yearCapture.capture(), anyInt());
    final int expectedLastYearOfRetention = CURRENT_YEAR - RETENTION_YEARS;
    assertThat(yearCapture.getValue()).isEqualTo(expectedLastYearOfRetention);
  }

  @Test
  void batchLoops() {
    when(deleteService.deletePeriodsOlderThan(anyInt(), anyInt()))
        .thenReturn(BATCH_SIZE)
        .thenReturn(BATCH_SIZE - 1);
    when(deleteService.deletePseudonymChainsWithoutPeriods(anyInt()))
        .thenReturn(BATCH_SIZE)
        .thenReturn(BATCH_SIZE)
        .thenReturn(0);
    when(deleteService.deleteChainsOrphans(anyInt())).thenReturn(BATCH_SIZE).thenReturn(1);

    underTest.purgeDatabase();

    final var periodsBatchSizeCapture = ArgumentCaptor.forClass(Integer.class);
    verify(deleteService, times(2))
        .deletePeriodsOlderThan(anyInt(), periodsBatchSizeCapture.capture());
    assertBatchSizes(periodsBatchSizeCapture.getAllValues(), 2);

    final var pseudoChainBatchSizeCapture = ArgumentCaptor.forClass(Integer.class);
    verify(deleteService, times(3))
        .deletePseudonymChainsWithoutPeriods(pseudoChainBatchSizeCapture.capture());
    assertBatchSizes(pseudoChainBatchSizeCapture.getAllValues(), 3);

    final var chainBatchSizeCapture = ArgumentCaptor.forClass(Integer.class);
    verify(deleteService, times(2)).deleteChainsOrphans(chainBatchSizeCapture.capture());
    assertBatchSizes(chainBatchSizeCapture.getAllValues(), 2);
  }

  private void assertBatchSizes(final List<Integer> captureValues, final int expectedBatchCount) {
    assertThat(captureValues).hasSize(expectedBatchCount).allMatch(limit -> limit == BATCH_SIZE);
  }

  @Test
  void deleteOrder() {
    underTest.purgeDatabase();

    final InOrder inOrder = inOrder(deleteService);
    inOrder.verify(deleteService).deletePeriodsOlderThan(anyInt(), anyInt());
    inOrder.verify(deleteService).deletePseudonymChainsWithoutPeriods(anyInt());
    inOrder.verify(deleteService).deleteChainsOrphans(anyInt());
  }
}
