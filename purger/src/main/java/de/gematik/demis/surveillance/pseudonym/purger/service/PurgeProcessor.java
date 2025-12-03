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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import de.gematik.demis.surveillance.pseudonym.purger.config.SpsPurgerConfigProps;
import java.time.LocalDate;
import java.util.function.IntUnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurgeProcessor {
  private final SpsPurgerConfigProps configProps;
  private final DeleteService deleteService;

  public void purgeDatabase() {
    final int maxYear = currentYear() - configProps.retentionYears();
    log.info("Purging periods with maxYear < {} from database...", maxYear);
    deleteInBatches("period", limit -> deleteService.deletePeriodsOlderThan(maxYear, limit));
    deleteInBatches("pseudonym_chain", deleteService::deletePseudonymChainsWithoutPeriods);
    deleteInBatches("chain", deleteService::deleteChainsOrphans);
  }

  private void deleteInBatches(final String type, final IntUnaryOperator sqlExecutor) {
    log.debug("Executing delete in batches for type {}", type);
    final int limit = configProps.batchSize();
    int total = 0;
    int batchCount = 0;
    int rowsDeleted;
    do {
      rowsDeleted = sqlExecutor.applyAsInt(limit);
      log.debug("{} {} rows deleted", rowsDeleted, type);
      batchCount++;
      total += rowsDeleted;
    } while (rowsDeleted == limit);
    log.info("Total {} rows deleted from {} in {} batches", total, type, batchCount);
  }

  private int currentYear() {
    return LocalDate.now().getYear();
  }
}
