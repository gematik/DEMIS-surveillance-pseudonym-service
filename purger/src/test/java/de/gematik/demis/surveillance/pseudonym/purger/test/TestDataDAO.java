package de.gematik.demis.surveillance.pseudonym.purger.test;

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

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestDataDAO {
  private final EntityManager em;

  @Transactional
  public void insertChain(final UUID chainId, final Instant createdAt) {
    final String sql = "insert into chain (chain_id, created_at) values (:chainId, :createdAt)";
    final int count =
        em.createNativeQuery(sql)
            .setParameter("chainId", chainId)
            .setParameter("createdAt", createdAt)
            .executeUpdate();
    assertThat(count).isEqualTo(1);
  }

  @Transactional
  public void insertPseudonymChain(final UUID chainId, final byte[] pseudonym) {
    final String sql =
        "insert into pseudonym_chain (pseudonym, chain_id) values (:pseudonym, :chainId)";
    final int count =
        em.createNativeQuery(sql)
            .setParameter("pseudonym", pseudonym)
            .setParameter("chainId", chainId)
            .executeUpdate();
    assertThat(count).isEqualTo(1);
  }

  @Transactional
  public void insertPeriod(
      final UUID chainId, final UUID periodId, final int minYear, final int maxYear) {
    final String sql =
        "insert into period (period_id, chain_id, min_year, max_year) values (:id, :chainId, :minYear, :maxYear)";
    final int count =
        em.createNativeQuery(sql)
            .setParameter("id", periodId)
            .setParameter("chainId", chainId)
            .setParameter("minYear", minYear)
            .setParameter("maxYear", maxYear)
            .executeUpdate();
    assertThat(count).isEqualTo(1);
  }

  @Transactional
  public void lockChains(
      final CountDownLatch locksEstablished,
      final CountDownLatch releaseLocks,
      final UUID... chainIds) {
    final String sql = "select 1 from chain where chain_id in :chainIds for update";
    em.createNativeQuery(sql).setParameter("chainIds", asList(chainIds)).getResultList();
    locksEstablished.countDown();
    try {
      releaseLocks.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public boolean existsChain(final UUID chainId) {
    return exists("chain", "chain_id", chainId);
  }

  public boolean existsPseudonymChain(final byte[] pseudonym) {
    return exists("pseudonym_chain", "pseudonym", pseudonym);
  }

  public boolean existsPeriod(final UUID periodId) {
    return exists("period", "period_id", periodId);
  }

  public int countChains() {
    return countRows("chain");
  }

  public int countPseudonymChains() {
    return countRows("pseudonym_chain");
  }

  public int countPeriods() {
    return countRows("period");
  }

  private boolean exists(final String tableName, final String pkColumn, final Object pkValue) {
    final String sql = "select count(*) from " + tableName + " where " + pkColumn + " = :id";
    final Number count =
        (Number) em.createNativeQuery(sql).setParameter("id", pkValue).getSingleResult();
    return count.longValue() > 0;
  }

  private int countRows(final String tableName) {
    final String sql = "select count(*) from " + tableName;
    final Number count = (Number) em.createNativeQuery(sql).getSingleResult();
    return count.intValue();
  }
}
