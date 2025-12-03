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

import static de.gematik.demis.surveillance.pseudonym.service.ErrorCode.CHAIN_MISSING;

import de.gematik.demis.surveillance.pseudonym.config.SpsPeriodConfig;
import de.gematik.demis.surveillance.pseudonym.entity.ChainEntity;
import de.gematik.demis.surveillance.pseudonym.entity.PeriodEntity;
import de.gematik.demis.surveillance.pseudonym.repository.ChainRepository;
import de.gematik.demis.surveillance.pseudonym.repository.PeriodRepository;
import de.gematik.demis.surveillance.pseudonym.service.ChainService.ChainResult;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to break down a pseudonym chain into periods with a maximal configured lifetime. The
 * identifier (UUID) of the period is used as new pseudonym. The periods are stored in the database
 * via the injected repository. The clock (injectable for testing purpose) is needed to obtain the
 * current date to determine whether a period reflects the current state of affairs
 */
@Service
@RequiredArgsConstructor
@Slf4j
class PeriodService {

  private final SpsPeriodConfig config;
  private final PeriodRepository periodRepository;
  private final ChainRepository chainRepository;
  private final Clock clock;

  /**
   * Returns the period ID (UUID) for a chain and a reference date.
   *
   * <p>Concurrency: <br>
   * At the beginning of this method, the chain is pessimistically locked for the entire duration of
   * the execution (=transaction). This ensures that concurrent calls targeting the same chain are
   * processed sequentially, while different chains can still be handled in parallel.
   *
   * <p>Flow:
   *
   * <ol>
   *   <li>Determine the adjusted reference year based on the configured deadline day (see {@link
   *       SpsPeriodConfig#adjustReferenceDay()}).
   *   <li>If the chain is newly created, immediately create a new period for this year.
   *   <li>Otherwise, load existing periods of the chain (descending by maxYear) and:
   *       <ul>
   *         <li>if the year is contained: return that period ID,
   *         <li>if the year is after the period: extend the period up to the year if within the
   *             configured max lifetime,
   *         <li>if the year is before the period: extend the period backward to the year if within
   *             the configured max lifetime, taking into account the special case that the current
   *             period must be open for one year in future
   *       </ul>
   *   <li>If none of the conditions apply, create a new period for the year.
   * </ol>
   *
   * Side effects:
   *
   * <ul>
   *   <li>May update existing period entities in the database (minYear/maxYear).
   *   <li>May create new period entities.
   * </ul>
   *
   * @param chain A valid chain. Must not be null
   * @param referenceDate Reference date used to calculate the adjusted reference year. Must not be
   *     null.
   * @return UUID of the resolved or newly created period.
   */
  @Transactional
  public UUID period(final ChainResult chain, final LocalDate referenceDate) {
    return periodForAdjustedReferenceYear(chain, adjust(referenceDate));
  }

  private UUID periodForAdjustedReferenceYear(final ChainResult chain, final int year) {
    lockChainExclusive(chain.chainId());

    final List<PeriodEntity> periodsOfChainOrderByMaxDateDesc =
        loadExistingPeriodsDescending(chain);
    log.debug("periods loaded: {}", periodsOfChainOrderByMaxDateDesc);

    final PeriodEntity result =
        findExistingPeriodWhereReferenceDateIsWithin(periodsOfChainOrderByMaxDateDesc, year)
            .or(() -> tryToExtendExistingPeriod(periodsOfChainOrderByMaxDateDesc, year))
            .orElseGet(() -> createNewPeriod(chain.chainId(), year));

    return result.getPeriodId();
  }

  private Optional<PeriodEntity> findExistingPeriodWhereReferenceDateIsWithin(
      final List<PeriodEntity> existingPeriods, final int adjustedReferenceYear) {
    for (final PeriodEntity period : existingPeriods) {
      if (isReferenceDateWithinPeriod(period, adjustedReferenceYear)) {
        return Optional.of(period);
      }
    }

    return Optional.empty();
  }

  private Optional<PeriodEntity> tryToExtendExistingPeriod(
      final List<PeriodEntity> existingPeriods, final int adjustedReferenceYear) {
    for (final PeriodEntity period : existingPeriods) {
      if (isReferenceDateAfterPeriod(period, adjustedReferenceYear)) {
        if (isExtendableToAfter(period, adjustedReferenceYear)) {
          period.setMaxYear(adjustedReferenceYear);
          periodRepository.save(period);
          return Optional.of(period);
        } else {
          // The other periods have an even earlier maximum date. We can stop here.
          return Optional.empty();
        }
      } else {
        // reference date must be before period
        if (isExtendableToBefore(period, adjustedReferenceYear)) {
          period.setMinYear(adjustedReferenceYear);
          periodRepository.save(period);
          return Optional.of(period);
        }
      }
    }

    return Optional.empty();
  }

  private void lockChainExclusive(final UUID chainId) {
    final ChainEntity chainEntity = chainRepository.getChainEntityByChainId(chainId);
    if (chainEntity == null) {
      throw CHAIN_MISSING.exception(
          "Chain with id " + chainId + " not found. Could not lock. Should never happen.");
    }
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  /** returns the absolut difference between two years. The order does not matter. */
  private int yearDiff(final int year1, final int year2) {
    return Math.abs(year1 - year2);
  }

  /**
   * loads all periods for the given chain. VERY IMPORTANT: The order of the entries is descending
   * by maxYear.
   */
  private List<PeriodEntity> loadExistingPeriodsDescending(final ChainResult chain) {
    return periodRepository.findByChainIdOrderByMaxYearDesc(chain.chainId());
  }

  /**
   * Return the adjusted year of the reference date.
   *
   * <p>Example:
   *
   * <ul>
   *   <li>Property: {@code sps.period.adjust-reference-day} = {@code --01-01}
   *   <li>{@code 2023-01-01} → {@code 2023}
   *   <li>{@code 2023-12-31} → {@code 2023}
   * </ul>
   *
   * <ul>
   *   <li>Property: {@code sps.period.adjust-reference-day} = {@code --07-01}
   *   <li>{@code 2023-06-30} → {@code 2022}
   *   <li>{@code 2023-07-01} → {@code 2023}
   * </ul>
   */
  private int adjust(final LocalDate referenceDate) {
    final int year = referenceDate.getYear();
    final LocalDate deadlineDay = config.adjustReferenceDay().atYear(year);
    return referenceDate.isBefore(deadlineDay) ? year - 1 : year;
  }

  private boolean isReferenceDateWithinPeriod(
      final PeriodEntity period, final int referenceDateYear) {
    return referenceDateYear >= period.getMinYear() && referenceDateYear <= period.getMaxYear();
  }

  private boolean isReferenceDateAfterPeriod(
      final PeriodEntity period, final int referenceDateYear) {
    return referenceDateYear > period.getMaxYear();
  }

  /**
   * Returns whether the given period represents the current state of affairs.
   *
   * <p>A period is considered current if it includes the current year or directly adjoins it (i.e.,
   * spans the current year or the immediately preceding year).
   *
   * <p>Example:
   *
   * <ul>
   *   <li>Current year: {@code 2025}
   *   <li>Returns {@code true} if the period covers {@code 2025} or {@code 2024}
   * </ul>
   *
   * <p>Note: The max year of the given period cannot be in the future (beyond the current year).
   *
   * @param period the period to evaluate
   * @return {@code true} if the period is current; {@code false} otherwise
   */
  private boolean isCurrentPeriod(final PeriodEntity period) {
    final int currentYear = adjust(today());
    return yearDiff(period.getMaxYear(), currentYear) <= 1;
  }

  private boolean isExtendableToAfter(final PeriodEntity period, final int referenceDateYear) {
    final int span = yearDiff(period.getMinYear(), referenceDateYear);
    return span < config.maxLifetimeInYears();
  }

  /**
   * Checks if the period is extendable backward to the given year.
   *
   * <p>Special rule: If the target period represents the current state of affairs, it must remain
   * extendable by one additional year into the future. To ensure this, the effective maximum
   * lifetime is reduced by one year for backward extensions of the current period.
   *
   * <p>In other words: when the period is current, subtract one year from {@code maxLifetime}
   * before checking whether a backward extension is still within the allowed span.
   */
  private boolean isExtendableToBefore(final PeriodEntity period, final int referenceDateYear) {
    final long span = yearDiff(period.getMaxYear(), referenceDateYear);
    final boolean current = isCurrentPeriod(period);
    return span < config.maxLifetimeInYears() - (current ? 1 : 0);
  }

  /** Insert a new period into database */
  private PeriodEntity createNewPeriod(final UUID chainId, final int year) {
    final PeriodEntity period = new PeriodEntity();
    // note: periodId is autoset by JPA
    period.setChainId(chainId);
    period.setMinYear(year);
    period.setMaxYear(year);
    return periodRepository.save(period);
  }
}
