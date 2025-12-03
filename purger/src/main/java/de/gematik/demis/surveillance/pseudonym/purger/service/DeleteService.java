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

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DeleteService {

  private static final String LIMIT = "limit";

  private final EntityManager em;

  @Transactional
  public int deletePeriodsOlderThan(final int year, final int limit) {
    final String sql =
        """
            DELETE FROM period
            WHERE period_id IN (
              SELECT period_id FROM period
              WHERE max_year < :maxYear
              ORDER BY period_id
              LIMIT :limit
            )
        """;

    final Query q = em.createNativeQuery(sql);
    q.setParameter("maxYear", year);
    q.setParameter(LIMIT, limit);
    return q.executeUpdate();
  }

  @Transactional
  public int deletePseudonymChainsWithoutPeriods(final int limit) {
    final String sql =
        """
            DELETE FROM pseudonym_chain
            WHERE pseudonym IN (
              SELECT pc.pseudonym
              FROM pseudonym_chain pc
              JOIN chain c on c.chain_id = pc.chain_id
              WHERE NOT EXISTS (SELECT 1 FROM period p WHERE p.chain_id = pc.chain_id)
              AND c.created_at < now() - interval '1 hour'
              ORDER BY c.created_at
              LIMIT :limit
            )
            """;

    final Query q = em.createNativeQuery(sql);
    q.setParameter(LIMIT, limit);
    return q.executeUpdate();
  }

  @Transactional
  public int deleteChainsOrphans(final int limit) {
    final String sql =
        """
            DELETE FROM chain
            WHERE chain_id IN (
              SELECT c.chain_id
              FROM chain c
              WHERE NOT EXISTS (SELECT 1 FROM period p WHERE p.chain_id = c.chain_id)
              AND c.created_at < now() - interval '1 hour'
              ORDER BY c.created_at
              LIMIT :limit
            )
            """;

    final Query q = em.createNativeQuery(sql);
    q.setParameter(LIMIT, limit);
    return q.executeUpdate();
  }
}
