package de.gematik.demis.surveillance.pseudonym;

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

import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.gematik.demis.surveillance.pseudonym.api.PseudonymInput;
import de.gematik.demis.surveillance.pseudonym.api.PseudonymOutput;
import de.gematik.demis.surveillance.pseudonym.config.SpsPeriodConfig;
import de.gematik.demis.surveillance.pseudonym.entity.ChainEntity;
import de.gematik.demis.surveillance.pseudonym.entity.PeriodEntity;
import de.gematik.demis.surveillance.pseudonym.repository.ChainRepository;
import de.gematik.demis.surveillance.pseudonym.repository.PeriodRepository;
import de.gematik.demis.surveillance.pseudonym.test.TestWithPostgresContainer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.converter.SimpleArgumentConverter;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integrationtest")
@Slf4j
class IntegrationSystemTest extends TestWithPostgresContainer {

  private static final String ENDPOINT = "/pseudonym";
  private static final LocalDate REFERENCE_DAY = LocalDate.of(2025, 8, 1);
  private static final Clock TODAY =
      Clock.fixed(Instant.parse("2025-08-05T00:00:00Z"), ZoneId.of("UTC"));

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private PeriodRepository periodRepository;
  @Autowired private ChainRepository chainRepository;
  @Autowired private AopAction chainRepositoryLockAopAction;
  @Autowired private AopAction pseudonymChainRepositoryReadAopAction;
  @MockitoSpyBean private SpsPeriodConfig config;

  @Test
  void differentPseudonyms_newChains() {
    final String periodId_1 = executeRestCall(input("1-a", "1-b", REFERENCE_DAY));
    final String periodId_2 = executeRestCall(input("1-c", "1-d", REFERENCE_DAY));
    assertThat(periodId_1).isNotEqualTo(periodId_2);
  }

  @Test
  void addPseudonymToChain_samePeriod() {
    final String periodId_1 = executeRestCall(input("2-a", "2-b", REFERENCE_DAY.minusYears(1)));
    final String periodId_2 = executeRestCall(input("2-b", "2-c", REFERENCE_DAY));
    final String periodId_3 = executeRestCall(input("2-d", "2-c", REFERENCE_DAY.plusMonths(1)));
    assertThat(periodId_1).isEqualTo(periodId_2).isEqualTo(periodId_3);
  }

  @Test
  void sameInputSameOutput() {
    final String periodId_1 = executeRestCall(input("3-a", "3-b", REFERENCE_DAY));
    final String periodId_2 = executeRestCall(input("3-a", "3-b", REFERENCE_DAY));
    assertThat(periodId_1).isEqualTo(periodId_2);
  }

  @Test
  void sameChain_differentPeriods() {
    final String periodId_1 = executeRestCall(input("3-a", "3-b", REFERENCE_DAY.minusYears(5)));
    final String periodId_2 = executeRestCall(input("3-a", "3-b", REFERENCE_DAY));
    assertThat(periodId_1).isNotEqualTo(periodId_2);
  }

  @Test
  void sequenceOfCallsForSamePatient() {
    // for all requests the input pseudonyms are equals
    final String pseudonym1 = "4-a";
    final String pseudonym2 = "4-b";

    // new chain, new period
    final String periodId_2025 = executeRestCall(input(pseudonym1, pseudonym2, 2025));
    final UUID chainId = getChainId(periodId_2025);
    assertPeriods(chainId, periodId_2025, List.of(range(2025, 2025)), 0);

    // same period (expanded before)
    final String periodId_2024 = executeRestCall(input(pseudonym1, pseudonym2, 2024));
    assertPeriods(chainId, periodId_2024, List.of(range(2024, 2025)), 0);

    // new period before
    final String periodId_2020 = executeRestCall(input(pseudonym1, pseudonym2, 2020));
    assertPeriods(chainId, periodId_2020, List.of(range(2024, 2025), range(2020, 2020)), 1);

    // same period (expanded before)
    final String periodId_2019 = executeRestCall(input(pseudonym1, pseudonym2, 2019));
    assertPeriods(chainId, periodId_2019, List.of(range(2024, 2025), range(2019, 2020)), 1);

    // same period (expanded after) -> full
    final String periodId_2021_1 = executeRestCall(input(pseudonym1, pseudonym2, 2021));
    assertPeriods(chainId, periodId_2021_1, List.of(range(2024, 2025), range(2019, 2021)), 1);

    // new period
    final String periodId_2022 = executeRestCall(input(pseudonym1, pseudonym2, 2022));
    assertPeriods(
        chainId,
        periodId_2022,
        List.of(range(2024, 2025), range(2022, 2022), range(2019, 2021)),
        1);

    // special case: expand older period due to current affairs (2024-25 must be expandable to
    // future year)
    final String periodId_2023 = executeRestCall(input(pseudonym1, pseudonym2, 2023));
    assertPeriods(
        chainId,
        periodId_2023,
        List.of(range(2024, 2025), range(2022, 2023), range(2019, 2021)),
        1);

    // no overlapping periods. existing periods take priority over younger periods.
    // no database change
    final String periodId_2021_2 = executeRestCall(input(pseudonym1, pseudonym2, 2021));
    assertPeriods(
        chainId,
        periodId_2021_2,
        List.of(range(2024, 2025), range(2022, 2023), range(2019, 2021)),
        2);

    // existing period, no database change
    final String periodId_2023_2 = executeRestCall(input(pseudonym1, pseudonym2, 2023));
    assertPeriods(
        chainId,
        periodId_2023_2,
        List.of(range(2024, 2025), range(2022, 2023), range(2019, 2021)),
        1);

    assertThat(periodId_2025).isEqualTo(periodId_2024);
    assertThat(periodId_2020)
        .isEqualTo(periodId_2019)
        .isEqualTo(periodId_2021_1)
        .isEqualTo(periodId_2021_2);
    assertThat(periodId_2022).isEqualTo(periodId_2023).isEqualTo(periodId_2023_2);
  }

  /**
   * All scenarios have been coordinated with RKI!
   *
   * <p>Maximal period lifetime: 5 years. Current year is always 2025.
   */
  @ParameterizedTest(name = "[{index}] {0}: {1})")
  @CsvSource(
      delimiter = ';',
      value = {
        "leave space for current year (2025); 2021->P1, 2024->P1, 2020->P0, 2025->P1",
        "leave space for next year (2026); 2022->P1, 2023->P1, 2024->P1, 2025 -> P1, 2021 -> P0",
        "leave no space for current year; 2021->P1, 2020->P1",
        "current year but period full; 2020->P1, 2021->P1, 2024->P1, 2025->P2",
        "prefer younger periods; 2024->P1, 2021->P1, 2026->P2, 2025->P2",
        "other order results in other periods; 2024->P1, 2021->P1, 2025->P1, 2026->P2",
        "prefer existing periods, no overlapping; 2020->P1, 2016->P1, 2010->P2, 2014->P2, 2015->P3, 2014->P2",
      })
  void sequenceOfCallsForSamePatientMoreScenarios(
      final String description,
      @ConvertWith(StringToYearPeriod.class) List<YearPeriod> inputSequence) {
    // The other tests are for max 3 years periods (configured in application-integrationtest.yaml).
    // In this test we want to have 5 years.
    when(config.maxLifetimeInYears()).thenReturn(5);

    final String pseudonym1 = UUID.randomUUID().toString();
    final String pseudonym2 = UUID.randomUUID().toString();

    final MultiValueMap<String, String> actualPeriodYearsMap = new LinkedMultiValueMap<>();
    final MultiValueMap<String, String> expectedPeriodYearsMap = new LinkedMultiValueMap<>();
    int callCounter = 0;
    for (final YearPeriod yearPeriod : inputSequence) {
      final String periodId = executeRestCall(input(pseudonym1, pseudonym2, yearPeriod.year()));
      callCounter++;
      final String calledYear = callCounter + "_" + yearPeriod.year();
      actualPeriodYearsMap.add(periodId, calledYear);
      expectedPeriodYearsMap.add(yearPeriod.expectedPeriod, calledYear);
    }
    assertThat(actualPeriodYearsMap.values())
        .containsExactlyElementsOf(expectedPeriodYearsMap.values());
  }

  @Test
  @Sql("/sql/pseudonym-chain-period-data.sql")
  void existingPseudonymPeriodInDatabase() {
    final String inputPseudonym_1_FromSql = "CL-PSEUDO-1";
    final String inputPseudonym_2_FromSql = "CL-PSEUDO-2";
    final LocalDate referenceDateFromSql = LocalDate.of(2023, 8, 1);
    final String expectedPeriodFromSql = "38c7c638-996a-4e10-a50b-bcdb24bbe6bd";
    final String actualPeriodId =
        executeRestCall(
            input(inputPseudonym_1_FromSql, inputPseudonym_2_FromSql, referenceDateFromSql));
    assertThat(actualPeriodId).isEqualTo(expectedPeriodFromSql);
  }

  private void assertPeriods(
      final UUID chainId,
      final String periodId,
      final List<Range> expectedPeriods,
      final int indexOfExpectedPeriod) {
    final List<PeriodEntity> periodsInDatabase =
        periodRepository.findByChainIdOrderByMaxYearDesc(chainId);
    final List<Range> actualPeriodRanges =
        periodsInDatabase.stream()
            .map(period -> range(period.getMinYear(), period.getMaxYear()))
            .toList();
    assertThat(actualPeriodRanges).containsExactlyElementsOf(expectedPeriods);
    assertThat(periodId)
        .isEqualTo(periodsInDatabase.get(indexOfExpectedPeriod).getPeriodId().toString());
  }

  private Range range(int minYear, int maxYear) {
    return new Range(minYear, maxYear);
  }

  private PeriodEntity getPeriodEntity(final String periodId) {
    assertThat(periodId).isNotBlank();
    return periodRepository.findById(UUID.fromString(periodId)).orElseThrow();
  }

  private UUID getChainId(final String periodId) {
    return getPeriodEntity(periodId).getChainId();
  }

  private String executeRestCall(final PseudonymInput input) {
    log.info("execute rest call for input: {}", input);

    final var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    final HttpEntity<PseudonymInput> request = new HttpEntity<>(input, headers);

    final ResponseEntity<PseudonymOutput> response =
        restTemplate.postForEntity(ENDPOINT, request, PseudonymOutput.class);

    log.info("Response {}", response);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    final PseudonymOutput result = response.getBody();
    assertThat(result).isNotNull();
    assertThat(result.system())
        .isEqualTo("https://demis.rki.de/fhir/sid/SurveillancePatientPseudonym");
    assertThat(result.value()).startsWith("urn:uuid:");

    final String newPseudonym = result.value().substring("urn:uuid:".length());
    assertThat(newPseudonym).isNotBlank();

    return newPseudonym;
  }

  private PseudonymInput input(
      final String pseudonym1, final String pseudonym2, final LocalDate referenceDate) {
    return new PseudonymInput(pseudonym1, pseudonym2, referenceDate);
  }

  private PseudonymInput input(
      final String pseudonym1, final String pseudonym2, final int yearOfreferenceDate) {
    return input(pseudonym1, pseudonym2, REFERENCE_DAY.withYear(yearOfreferenceDate));
  }

  private static class StringToYearPeriod extends SimpleArgumentConverter {

    private final Pattern pattern = Pattern.compile("(\\d{4})\\s*->\\s*(P\\w+)");

    @Override
    protected Object convert(final Object source, final Class<?> targetType)
        throws ArgumentConversionException {
      if (!List.class.isAssignableFrom(targetType)) {
        throw new ArgumentConversionException("Target type must be List");
      }
      if (source == null) return List.of();
      return pattern
          .matcher(source.toString())
          .results()
          .map(m -> new YearPeriod(Integer.parseInt(m.group(1)), m.group(2)))
          .toList();
    }
  }

  private record YearPeriod(int year, String expectedPeriod) {}

  @TestConfiguration
  static class IntegrationSystemTestConfiguration {
    @Primary
    @Bean
    Clock today() {
      return TODAY;
    }
  }

  @TestConfiguration
  @EnableAspectJAutoProxy
  @Aspect
  static class AopTestConfiguration {
    private final AopAction chainRepositoryLockAopAction = new AopAction();
    private final AopAction pseudonymChainRepositoryReadAopAction = new AopAction();

    @Bean
    AopAction chainRepositoryLockAopAction() {
      return chainRepositoryLockAopAction;
    }

    @Bean
    AopAction pseudonymChainRepositoryReadAopAction() {
      return pseudonymChainRepositoryReadAopAction;
    }

    @Around(
        "execution(* de.gematik.demis.surveillance.pseudonym.repository.ChainRepository.getChainEntityByChainId(..))")
    public Object aroundLockChain(ProceedingJoinPoint pjp) throws Throwable {
      return handleAop(pjp, chainRepositoryLockAopAction);
    }

    @Around(
        "execution(* de.gematik.demis.surveillance.pseudonym.repository.PseudonymChainRepository.findByPseudonymIn(..))")
    public Object aroundFindPseudonymChains(ProceedingJoinPoint pjp) throws Throwable {
      return handleAop(pjp, pseudonymChainRepositoryReadAopAction);
    }

    private Object handleAop(final ProceedingJoinPoint pjp, final AopAction aopAction)
        throws Throwable {
      ofNullable(aopAction.getBefore()).ifPresent(Runnable::run);
      Object result = pjp.proceed();
      ofNullable(aopAction.getAfter()).ifPresent(Runnable::run);
      return result;
    }
  }

  @Data
  @NoArgsConstructor
  private static class AopAction {
    private Runnable before;
    private Runnable after;
  }

  private record Range(int minYear, int maxYear) {}

  @Nested
  class Concurrency {

    @Test
    @Sql("/sql/db-clean.sql")
    void concurrentCreateNewChainWithNewPeriod() throws Exception {
      final var input = input("6-a", "6-b", 2020);

      final int numberOfConcurrentThreads = 2;
      final CountDownLatch syncReadPseudoChains =
          syncThreadsAfterReadPseudonymChainTable(numberOfConcurrentThreads);
      final CountDownLatch syncBeforeChainLock =
          syncThreadsBeforeChainLock(numberOfConcurrentThreads);

      final String[] periods = executeParallel(numberOfConcurrentThreads, input, input);

      assertThat(syncReadPseudoChains.getCount())
          .as("expected read method was not called")
          .isZero();
      assertThat(syncBeforeChainLock.getCount()).as("lock chain method was not called").isZero();

      assertThat(periods).hasSize(2);
      assertThat(periods[0]).isEqualTo(periods[1]);
      final UUID chainId = getChainId(periods[0]);

      // assert duplicated chain was roll backed
      final List<UUID> allChainIds = getAllChainIdsFromDatabase();
      assertThat(allChainIds).containsExactly(chainId);
    }

    private List<UUID> getAllChainIdsFromDatabase() {
      final Iterable<ChainEntity> allChains = chainRepository.findAll();
      return StreamSupport.stream(allChains.spliterator(), false)
          .map(ChainEntity::getChainId)
          .toList();
    }

    @Test
    void concurrentPeriodsUpdates() throws Exception {
      final String pseudonym1 = "5-a";
      final String pseudonym2 = "5-b";
      final int year = 2020;
      final var input0 = input(pseudonym1, pseudonym2, year);
      final var input1 = input(pseudonym1, pseudonym2, year + 1);
      final var input2 = input(pseudonym1, pseudonym2, year - 1);

      // create chain and period without concurrency
      final String period = executeRestCall(input0);

      final int numberOfConcurrentThreads = 2;
      final CountDownLatch countDownLatch = syncThreadsBeforeChainLock(numberOfConcurrentThreads);
      final String[] periods = executeParallel(numberOfConcurrentThreads, input1, input2);
      assertThat(countDownLatch.getCount()).as("lock chain method was not called").isZero();

      assertThat(periods[0]).isEqualTo(period);
      assertThat(periods[1]).isEqualTo(period);

      final PeriodEntity periodEntity = getPeriodEntity(period);
      assertThat(periodEntity.getMinYear()).as("minYear").isEqualTo(year - 1);
      assertThat(periodEntity.getMaxYear()).as("maxYear").isEqualTo(year + 1);
    }

    private CountDownLatch syncThreadsAfterReadPseudonymChainTable(
        final int numberOfConcurrentThreads) {
      final CountDownLatch waitLock = new CountDownLatch(numberOfConcurrentThreads);
      pseudonymChainRepositoryReadAopAction.setAfter(() -> pauseExecution(waitLock));
      return waitLock;
    }

    private CountDownLatch syncThreadsBeforeChainLock(final int numberOfConcurrentThreads) {
      final CountDownLatch waitLock = new CountDownLatch(numberOfConcurrentThreads);
      chainRepositoryLockAopAction.setBefore(() -> pauseExecution(waitLock));
      return waitLock;
    }

    private String[] executeParallel(
        final int numberOfConcurrentThreads, final PseudonymInput... inputs)
        throws InterruptedException, ExecutionException {
      try (final ExecutorService pool = Executors.newFixedThreadPool(numberOfConcurrentThreads)) {
        final List<Callable<String>> calls =
            Arrays.stream(inputs)
                .<Callable<String>>map(input -> () -> executeRestCall(input))
                .toList();
        final List<Future<String>> futures = pool.invokeAll(calls);
        final String[] result = new String[futures.size()];
        for (int i = 0; i < futures.size(); i++) {
          result[i] = futures.get(i).get();
        }
        return result;
      }
    }

    private void pauseExecution(final CountDownLatch waitLock) {
      waitLock.countDown();
      final boolean noTimeout;
      try {
        noTimeout = waitLock.await(2, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
      assertThat(noTimeout).isTrue();
    }
  }
}
