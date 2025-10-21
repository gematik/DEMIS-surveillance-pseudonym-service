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

import static java.lang.Integer.parseInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import de.gematik.demis.service.base.error.ServiceException;
import de.gematik.demis.surveillance.pseudonym.config.SpsPeriodConfig;
import de.gematik.demis.surveillance.pseudonym.entity.ChainEntity;
import de.gematik.demis.surveillance.pseudonym.entity.PeriodEntity;
import de.gematik.demis.surveillance.pseudonym.repository.ChainRepository;
import de.gematik.demis.surveillance.pseudonym.repository.PeriodRepository;
import de.gematik.demis.surveillance.pseudonym.service.ChainService.ChainResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.converter.JavaTimeConversionPattern;
import org.junit.jupiter.params.converter.SimpleArgumentConverter;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.data.util.Pair;

@ExtendWith(MockitoExtension.class)
class PeriodServiceTest {

  private static final MonthDay ADJUST_REFERENCE_DAY = MonthDay.of(Month.JULY, 1);
  private static final int MAX_PERIOD_LIFETIME_IN_YEARS = 3;
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2025-08-01T00:00:00Z"), ZoneId.of("UTC"));
  private static final UUID NEW_GENERATED_UUID = UUID.randomUUID();

  private static final ObjectMapper MAPPER = new JsonMapper();

  @Mock private ChainRepository chainRepository;
  @Mock private PeriodRepository periodRepository;
  private PeriodService underTest;
  private UUID chainId;
  @Captor private ArgumentCaptor<PeriodEntity> periodEntityCaptor;
  private List<PeriodEntity> existingPeriodsSnapshot;
  private List<PeriodEntity> existingPeriods;

  @SneakyThrows
  private static List<PeriodEntity> copyList(final List<PeriodEntity> object) {
    return MAPPER.readValue(MAPPER.writeValueAsString(object), new TypeReference<>() {});
  }

  @BeforeEach
  void setup() {
    final SpsPeriodConfig config =
        SpsPeriodConfig.builder()
            .adjustReferenceDay(ADJUST_REFERENCE_DAY)
            .maxLifetimeInYears(MAX_PERIOD_LIFETIME_IN_YEARS)
            .build();
    underTest = new PeriodService(config, periodRepository, chainRepository, FIXED_CLOCK);
    chainId = UUID.randomUUID();
  }

  @Test
  void newChain_shouldCreateNewPeriod() {
    final LocalDate referenceDate = LocalDate.of(2025, 8, 15);
    mockLockChainCall();
    mockDatabase(List.of());
    mockSaveRepository();

    final UUID result = underTest.period(new ChainResult(chainId, true), referenceDate);

    assertDatabaseInsert(2025, 2025);
    assertThat(result).isEqualTo(NEW_GENERATED_UUID);
  }

  @Test
  void referenceDateWithinExistingPeriod_noDatabaseChange() {
    final LocalDate referenceDate = LocalDate.of(2021, 8, 15);
    final PeriodEntity expectedPeriod = period(2020, 2022);
    mockLockChainCall();
    mockDatabase(List.of(period(2023, 2023), expectedPeriod, period(2019, 2019)));

    final UUID result = underTest.period(existingChain(), referenceDate);

    assertThat(result).isNotNull().isEqualTo(expectedPeriod.getPeriodId());
    verify(periodRepository, never()).save(any());
  }

  @ParameterizedTest(name = "[{index}] {0}: {1} # {2} -> {3}={4}")
  @CsvSource(
      delimiter = ';',
      value = {
        "after period; 2021-2022; 2023-08-15; 0; 2021-2023",
        "before period; 2021-2022; 2020-08-15; 0; 2020-2022",
        "after period multiple; 2025-2025|2021-2022; 2024-08-15; 0; 2024-2025",
        "after period multiple with special case current year 2025; 2024-2025|2021-2022; 2023-08-15; 1; 2021-2023",
        "before period multiple; 2023-2025|2021-2022; 2020-08-15; 1; 2020-2022",
      })
  void existingPeriodShouldExtend(
      final String description,
      @ConvertWith(CsvToMinMaxYearList.class)
          final List<Pair<Integer, Integer>> existingPeriodMinMaxYearList,
      @JavaTimeConversionPattern("yyyy-MM-dd") final LocalDate referenceDate,
      final int indexOfChangedPeriod,
      @ConvertWith(CsvToMinMaxYear.class) final Pair<Integer, Integer> expectedExtendedPeriod) {
    final List<PeriodEntity> periodsInDatabase = toPeriods(existingPeriodMinMaxYearList);
    final UUID expectedPeriodUUID = periodsInDatabase.get(indexOfChangedPeriod).getPeriodId();
    mockLockChainCall();
    mockDatabase(periodsInDatabase);

    final UUID result = underTest.period(existingChain(), referenceDate);

    assertThat(result).isNotNull().isEqualTo(expectedPeriodUUID);
    assertDatabaseSave(
        expectedPeriodUUID, expectedExtendedPeriod.getFirst(), expectedExtendedPeriod.getSecond());
  }

  @ParameterizedTest(name = "[{index}] {0}: {1} # {2} -> {3}")
  @CsvSource(
      delimiter = ';',
      value = {
        "after period; 2020-2022; 2023-08-15; 2023",
        "before period; 2022-2024; 2021-08-15; 2021",
        "before period current year (2025) special case; 2024-2025; 2023-08-15; 2023",
        "no existing periods; ; 2023-08-15; 2023"
      })
  void existingPeriodsAreNotExtendable_shouldInsertNewPeriod(
      final String description,
      @ConvertWith(CsvToMinMaxYearList.class)
          final List<Pair<Integer, Integer>> existingPeriodMinMaxYearList,
      @JavaTimeConversionPattern("yyyy-MM-dd") final LocalDate referenceDate,
      final int newPeriodMinMaxDateYear) {
    final List<PeriodEntity> periodsInDatabase = toPeriods(existingPeriodMinMaxYearList);

    mockLockChainCall();
    mockDatabase(periodsInDatabase);
    mockSaveRepository();

    final UUID result = underTest.period(existingChain(), referenceDate);

    assertExistingPeriodsAreUnmodified();
    assertThat(result).isEqualTo(NEW_GENERATED_UUID);
    assertDatabaseInsert(newPeriodMinMaxDateYear, newPeriodMinMaxDateYear);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = ';',
      value = {
        "2024-01-01; 2023",
        "2024-06-30; 2023",
        "2024-07-01; 2024",
        "2024-12-31; 2024",
        "2025-01-01; 2024"
      })
  void adjustReferenceDay(
      @JavaTimeConversionPattern("yyyy-MM-dd") final LocalDate referenceDate,
      final int expectedAdjustedYear) {
    mockLockChainCall();
    mockSaveRepository();
    underTest.period(existingChain(), referenceDate);
    assertDatabaseInsert(expectedAdjustedYear, expectedAdjustedYear);
  }

  @Test
  void chainNotFoundInDatabaseThrowsException() {
    final LocalDate referenceDate = LocalDate.now();
    final ChainResult notExistingChain = new ChainResult(UUID.randomUUID(), false);
    when(chainRepository.getChainEntityByChainId(notExistingChain.chainId())).thenReturn(null);
    final ServiceException thrownException =
        catchThrowableOfType(
            ServiceException.class, () -> underTest.period(notExistingChain, referenceDate));
    assertThat(thrownException).isNotNull();
    assertThat(thrownException.getErrorCode()).isEqualTo(ErrorCode.CHAIN_MISSING.getCode());
  }

  private void mockLockChainCall() {
    final ChainEntity chainEntity = new ChainEntity();
    chainEntity.setChainId(chainId);
    when(chainRepository.getChainEntityByChainId(chainId)).thenReturn(chainEntity);
  }

  private void assertExistingPeriodsAreUnmodified() {
    assertThat(existingPeriods).usingRecursiveComparison().isEqualTo(existingPeriodsSnapshot);
  }

  private void assertDatabaseInsert(final int minDate, final int maxDate) {
    assertDatabaseSave(null, minDate, maxDate);
  }

  private void assertDatabaseSave(
      final UUID expectedPeriodId, final int minYear, final int maxYear) {
    verify(periodRepository, times(1)).save(periodEntityCaptor.capture());
    final PeriodEntity saved = periodEntityCaptor.getValue();
    assertThat(saved.getChainId()).isEqualTo(chainId);
    assertThat(saved.getMinYear()).isEqualTo(minYear);
    assertThat(saved.getMaxYear()).isEqualTo(maxYear);
    assertThat(saved.getPeriodId()).isEqualTo(expectedPeriodId);
  }

  private ChainResult existingChain() {
    return new ChainResult(chainId, false);
  }

  private void mockDatabase(final List<PeriodEntity> periods) {
    existingPeriods = periods;
    existingPeriodsSnapshot = copyList(periods);
    when(periodRepository.findByChainIdOrderByMaxYearDesc(chainId)).thenReturn(periods);
  }

  private void mockSaveRepository() {
    doAnswer(
            invocation -> {
              final PeriodEntity entity = invocation.getArgument(0);
              if (entity.getPeriodId() == null) {
                // insert
                final PeriodEntity copy = new PeriodEntity();
                BeanUtils.copyProperties(entity, copy);
                copy.setPeriodId(NEW_GENERATED_UUID);
                return copy;
              } else {
                // update
                return entity;
              }
            })
        .when(periodRepository)
        .save(any(PeriodEntity.class));
  }

  private List<PeriodEntity> toPeriods(
      final List<Pair<Integer, Integer>> existingPeriodMinMaxYearList) {
    return existingPeriodMinMaxYearList.stream()
        .map(minMax -> period(minMax.getFirst(), minMax.getSecond()))
        .toList();
  }

  private PeriodEntity period(final int min, final int max) {
    final PeriodEntity e = new PeriodEntity();
    e.setPeriodId(UUID.randomUUID());
    e.setChainId(chainId);
    e.setMinYear(min);
    e.setMaxYear(max);
    return e;
  }

  static class CsvToMinMaxYear extends SimpleArgumentConverter {
    private static final Pattern pattern = Pattern.compile("^(\\d{4})-(\\d{4})$");

    static Pair<Integer, Integer> stringToIntPair(final String s) {
      final Matcher matcher = pattern.matcher(s);
      if (!matcher.matches()) {
        throw new ArgumentConversionException("List entries must be of the form yyyy-yyyy");
      }
      return Pair.of(parseInt(matcher.group(1)), parseInt(matcher.group(2)));
    }

    @Override
    protected Pair<Integer, Integer> convert(final Object source, final Class<?> targetType)
        throws ArgumentConversionException {
      if (!Pair.class.isAssignableFrom(targetType)) {
        throw new ArgumentConversionException("Target type must be List");
      }
      if (source == null) return null;
      return stringToIntPair(source.toString());
    }
  }

  private static class CsvToMinMaxYearList extends SimpleArgumentConverter {

    /** YEAR_MIN_1-YEAR_MAX_1|YEAR_MIN_2-YEAR_MAX_2|... */
    @Override
    protected List<Pair<Integer, Integer>> convert(final Object source, final Class<?> targetType)
        throws ArgumentConversionException {
      if (source == null) return List.of();
      if (!List.class.isAssignableFrom(targetType)) {
        throw new ArgumentConversionException("Target type must be List");
      }
      final String s = source.toString().trim();
      if (s.isEmpty()) return List.of();
      return Arrays.stream(s.split("\\|"))
          .map(String::trim)
          .filter(t -> !t.isEmpty())
          .map(CsvToMinMaxYear::stringToIntPair)
          .toList();
    }
  }
}
