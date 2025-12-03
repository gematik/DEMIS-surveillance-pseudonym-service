package de.gematik.demis.surveillance.pseudonym.purger;

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

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.surveillance.pseudonym.purger.service.PurgeProcessor;
import de.gematik.demis.surveillance.pseudonym.purger.test.TestDataDAO;
import de.gematik.demis.surveillance.pseudonym.purger.test.TestWithPostgresContainer;
import de.gematik.demis.surveillance.pseudonym.purger.test.TxCounter;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "sps.purger.batch-size=" + PurgerIntegrationSystemTest.BATCH_SIZE,
      "sps.purger.retention-years=" + PurgerIntegrationSystemTest.RETENTION_YEARS
    })
@ActiveProfiles("integrationtest")
@Import(TxCounter.class)
@Slf4j
class PurgerIntegrationSystemTest extends TestWithPostgresContainer {

  public static final int BATCH_SIZE = 40;
  public static final int RETENTION_YEARS = 10;
  private static final int LAST_RETENTION_YEAR = LocalDate.now().getYear() - RETENTION_YEARS;

  @Autowired PurgeProcessor underTest;
  @Autowired TestDataDAO dao;
  @Autowired TxCounter txCounter;

  private static byte[] random32Bytes() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return bytes;
  }

  @Test
  void deletePeriodAndEntireChain() {
    final UUID chainId = createChain();
    final List<byte[]> pseudonyms = createPseudonyms(chainId, 4);
    final UUID periodToDelete =
        createPeriod(chainId, LAST_RETENTION_YEAR - 5, LAST_RETENTION_YEAR - 1);

    underTest.purgeDatabase();

    assertChainWithPseudonymsExists(chainId, pseudonyms, false);
    assertPeriodExists(periodToDelete, false);
  }

  @Test
  void chainWithMultiplePeriodsDeleteOnlyOnePeriodNotTheChain() {
    final UUID chainId = createChain();
    final List<byte[]> pseudonyms = createPseudonyms(chainId, 2);
    final UUID periodToDelete =
        createPeriod(chainId, LAST_RETENTION_YEAR - 1, LAST_RETENTION_YEAR - 1);
    final UUID periodToRetain = createPeriod(chainId, LAST_RETENTION_YEAR, LAST_RETENTION_YEAR);

    underTest.purgeDatabase();

    assertChainWithPseudonymsExists(chainId, pseudonyms, true);
    assertPeriodExists(periodToDelete, false);
    assertPeriodExists(periodToRetain, true);
  }

  @Test
  void doNotDeletePeriod() {
    final UUID chainId = createChain();
    final List<byte[]> pseudonyms = createPseudonyms(chainId, 2);
    final UUID periodId = createPeriod(chainId, LAST_RETENTION_YEAR - 5, LAST_RETENTION_YEAR);

    underTest.purgeDatabase();

    assertChainWithPseudonymsExists(chainId, pseudonyms, true);
    assertPeriodExists(periodId, true);
  }

  @Test
  void doNotDeleteNewlyCreatedChains() {
    final UUID chainId = createChain(Instant.now());
    final List<byte[]> pseudonyms = createPseudonyms(chainId, 2);

    underTest.purgeDatabase();

    assertChainWithPseudonymsExists(chainId, pseudonyms, true);
  }

  @Test
  @Sql("/sql/db-clean.sql")
  void deleteBatching() {
    // create 100 chain with each 10 pseudonyms and each 2 periods to delete
    final int chainCount = 100;
    final int pseudonymCountPerChain = 10;
    final int periodCountPerChain = 2;
    for (int i = 1; i <= chainCount; i++) {
      final UUID chainId = createChain();
      createPseudonyms(chainId, pseudonymCountPerChain);
      createPeriod(chainId, LAST_RETENTION_YEAR - 1, LAST_RETENTION_YEAR - 1);
      createPeriod(chainId, LAST_RETENTION_YEAR - 2, LAST_RETENTION_YEAR - 2);
    }

    // check that inserts were successfully
    final int totalPseudonymCount = chainCount * pseudonymCountPerChain;
    final int totalPeriodCount = chainCount * periodCountPerChain;
    assertThat(dao.countChains()).isEqualTo(chainCount);
    assertThat(dao.countPseudonymChains()).isEqualTo(totalPseudonymCount);
    assertThat(dao.countPeriods()).isEqualTo(totalPeriodCount);

    txCounter.reset();
    underTest.purgeDatabase();

    // assert that everything is deleted
    assertThat(dao.countChains()).isZero();
    assertThat(dao.countPseudonymChains()).isZero();
    assertThat(dao.countPeriods()).isZero();

    // assert transaction count
    final int expectedTransactions =
        batchCount(chainCount) + batchCount(totalPseudonymCount) + batchCount(totalPeriodCount);
    assertThat(txCounter.getCommits()).isEqualTo(expectedTransactions);
    assertThat(txCounter.getRollbacks()).isZero();
  }

  private int batchCount(final int rows) {
    return (rows / BATCH_SIZE) + 1;
  }

  @Test
  void purgerRunWhileServiceIsRunningAndHoldsPessimisticLocks() throws Exception {
    // setup database
    final UUID newlyCreatedChainToLock = createChain(Instant.now());
    createPseudonyms(newlyCreatedChainToLock, 2);

    final UUID chainToDelete = createChain();
    createPseudonyms(chainToDelete, 100);
    createPeriod(chainToDelete, LAST_RETENTION_YEAR - 1, LAST_RETENTION_YEAR - 1);

    final UUID existingChainToLock = createChain();
    createPseudonyms(existingChainToLock, 200);
    createPeriod(existingChainToLock, LAST_RETENTION_YEAR + 8, LAST_RETENTION_YEAR + 10);

    final AtomicReference<Thread> deleteThreadRef = new AtomicReference<>();
    try (final ExecutorService executor = Executors.newFixedThreadPool(2)) {
      final CountDownLatch locksEstablished = new CountDownLatch(1);
      final CountDownLatch releaseLocks = new CountDownLatch(1);

      // first lock chain rows in own thread.
      executor.submit(
          () -> {
            dao.lockChains(
                locksEstablished, releaseLocks, existingChainToLock, newlyCreatedChainToLock);
          });
      // wait until rows are locked in database
      assertThat(locksEstablished.await(2, SECONDS)).as("database locks established").isTrue();

      // then execute purger in another thread
      // Note: If the locked chains should be deleted (which is here not the case), the purger has
      // to wait for the lock and is blocked.
      final Future<?> serviceUnderTestFeature =
          executor.submit(
              () -> {
                deleteThreadRef.set(Thread.currentThread());
                underTest.purgeDatabase();
              });

      try {
        log.info("Start Purger");
        // and wait at most 3 seconds for completion.
        serviceUnderTestFeature.get(3, SECONDS);
        log.info("End Purger");
      } catch (final TimeoutException e) {
        log.info("Purger timed out");
        final StackTraceElement[] stackTrace = deleteThreadRef.get().getStackTrace();
        log.info("Stacktrace of purger thread: {}", Arrays.toString(stackTrace));
        serviceUnderTestFeature.cancel(true);
        Assertions.fail("purger execution is blocked. Cancel. Test failed");
      } finally {
        releaseLocks.countDown();
      }
    }
  }

  private void assertChainWithPseudonymsExists(
      final UUID chainId, final List<byte[]> pseudonyms, final boolean expected) {
    assertThat(dao.existsChain(chainId)).as("chain exists").isEqualTo(expected);
    assertThat(pseudonyms)
        .isNotEmpty()
        .as("pseudonyms exists")
        .allSatisfy(
            pseudonym -> assertThat(dao.existsPseudonymChain(pseudonym)).isEqualTo(expected));
  }

  private void assertPeriodExists(final UUID periodId, final boolean expected) {
    assertThat(dao.existsPeriod(periodId)).as("period exists").isEqualTo(expected);
  }

  private UUID createChain() {
    return createChain(Instant.now().minus(Duration.ofHours(2)));
  }

  private UUID createChain(final Instant createdAt) {
    final UUID chainId = UUID.randomUUID();
    dao.insertChain(chainId, createdAt);
    return chainId;
  }

  private List<byte[]> createPseudonyms(final UUID chainId, int count) {
    final List<byte[]> pseudonyms = range(0, count).mapToObj(i -> random32Bytes()).toList();
    pseudonyms.forEach(pseudonym -> dao.insertPseudonymChain(chainId, pseudonym));
    return pseudonyms;
  }

  private UUID createPeriod(final UUID chainId, final int minYear, final int maxYear) {
    final UUID periodId = UUID.randomUUID();
    dao.insertPeriod(chainId, periodId, minYear, maxYear);
    return periodId;
  }
}
