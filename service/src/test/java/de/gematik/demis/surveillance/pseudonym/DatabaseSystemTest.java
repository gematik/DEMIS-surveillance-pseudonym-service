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

import static java.util.stream.IntStream.rangeClosed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import de.gematik.demis.surveillance.pseudonym.entity.ChainEntity;
import de.gematik.demis.surveillance.pseudonym.entity.PeriodEntity;
import de.gematik.demis.surveillance.pseudonym.entity.PseudonymChainEntity;
import de.gematik.demis.surveillance.pseudonym.repository.ChainRepository;
import de.gematik.demis.surveillance.pseudonym.repository.PeriodRepository;
import de.gematik.demis.surveillance.pseudonym.repository.PseudonymChainRepository;
import de.gematik.demis.surveillance.pseudonym.test.TestWithPostgresContainer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integrationtest")
@Slf4j
class DatabaseSystemTest extends TestWithPostgresContainer {

  @Autowired private ChainRepository chainRepository;

  private void saveChain(final UUID chainId) {
    final ChainEntity entity = new ChainEntity();
    entity.setChainId(chainId);
    chainRepository.save(entity);
  }

  @Nested
  class ChainTable {
    @Test
    void saveAndLoadEntity() {
      final ChainEntity entity = new ChainEntity();
      final UUID id = UUID.randomUUID();
      entity.setChainId(id);

      assertThat(entity.isNew()).isTrue();
      chainRepository.save(entity);
      assertThat(entity.isNew()).isFalse();

      final Optional<ChainEntity> fromDatabase = chainRepository.findById(id);

      assertThat(fromDatabase).isPresent();
      final ChainEntity fromDbEntity = fromDatabase.get();
      assertThat(fromDbEntity)
          .usingRecursiveComparison()
          .ignoringFields("createdAt")
          .isEqualTo(entity);
      assertThat(fromDbEntity.getCreatedAt())
          .isCloseTo(Instant.now(), within(3, ChronoUnit.SECONDS));
    }
  }

  @Nested
  class PseudonymChainTable {
    @Autowired PseudonymChainRepository pseudonymChainRepository;

    private UUID chainId;

    @BeforeEach
    void setUpChain() {
      chainId = UUID.randomUUID();
      saveChain(chainId);
    }

    @Test
    void saveAndLoadPseudonymChainEntry() {
      final PseudonymChainEntity entity = new PseudonymChainEntity();
      entity.setPseudonym(validPseudonym(0));
      entity.setChainId(chainId);

      assertThat(entity.isNew()).isTrue();
      pseudonymChainRepository.save(entity);
      assertThat(entity.isNew()).isFalse();

      final Optional<PseudonymChainEntity> fromDatabase =
          pseudonymChainRepository.findById(entity.getPseudonym());

      assertThat(fromDatabase).isPresent();
      assertThat(fromDatabase.get()).usingRecursiveComparison().isEqualTo(entity);
    }

    @Test
    void duplicateKey() {
      final var primaryKey = validPseudonym(1);
      final PseudonymChainEntity entity = new PseudonymChainEntity();
      entity.setPseudonym(primaryKey);
      entity.setChainId(chainId);
      pseudonymChainRepository.save(entity);

      final PseudonymChainEntity duplicate = new PseudonymChainEntity();
      duplicate.setPseudonym(primaryKey);
      final UUID otherChainId = UUID.randomUUID();
      saveChain(otherChainId);
      duplicate.setChainId(otherChainId);

      assertThatThrownBy(() -> pseudonymChainRepository.save(duplicate))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("pk_pseudonym_chain");
    }

    @Test
    void checkConstraintPseudonymLength() {
      final PseudonymChainEntity entity = new PseudonymChainEntity();
      final byte[] tooShortPseudonym = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9};
      entity.setPseudonym(tooShortPseudonym);
      entity.setChainId(chainId);

      assertThatThrownBy(() -> pseudonymChainRepository.save(entity))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("chk_pseudonym_length_32");
    }

    @Test
    void checkChainIdNotNullConstraint() {
      final PseudonymChainEntity entity = new PseudonymChainEntity();
      entity.setPseudonym(validPseudonym(2));
      entity.setChainId(null);

      assertThatThrownBy(() -> pseudonymChainRepository.save(entity))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("chain_id");
    }

    @Test
    void checkFkChainId() {
      final PseudonymChainEntity entity = new PseudonymChainEntity();
      entity.setPseudonym(validPseudonym(2));
      // reference to a not existing chain
      entity.setChainId(UUID.randomUUID());

      assertThatThrownBy(() -> pseudonymChainRepository.save(entity))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("fk_pseudonym_chain_chain");
    }

    private byte[] validPseudonym(int number) {
      return rangeClosed(1, 32)
          .collect(() -> new byte[32], (a, i) -> a[i - 1] = (byte) (i + number), (a1, a2) -> {});
    }
  }

  @Nested
  class PeriodTable {
    @Autowired PeriodRepository periodRepository;

    private PeriodEntity createValidPeriodEntity() {
      final PeriodEntity entity = new PeriodEntity();
      final UUID chainId = UUID.randomUUID();
      saveChain(chainId);

      entity.setChainId(chainId);
      entity.setMinYear(2024);
      entity.setMaxYear(2025);
      return entity;
    }

    @Test
    void saveAndLoadPeriodEntry() {
      final PeriodEntity entity = createValidPeriodEntity();

      assertThat(entity.getPeriodId()).isNull();

      final PeriodEntity result = periodRepository.save(entity);
      assertThat(result.getPeriodId()).isNotNull();

      final Optional<PeriodEntity> fromDatabase = periodRepository.findById(result.getPeriodId());

      assertThat(fromDatabase).isPresent();
      assertThat(fromDatabase.get()).usingRecursiveComparison().isEqualTo(entity);
    }

    @Test
    void checkNotNullConstraintChainId() {
      final PeriodEntity entity = createValidPeriodEntity();
      entity.setChainId(null);

      assertThatThrownBy(() -> periodRepository.save(entity))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining(
              "null value in column \"chain_id\" of relation \"period\" violates not-null constraint");
    }

    @Test
    void checkNotNullConstraintMinYear() {
      final PeriodEntity entity = createValidPeriodEntity();
      entity.setMinYear(null);

      assertThatThrownBy(() -> periodRepository.save(entity))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining(
              "null value in column \"min_year\" of relation \"period\" violates not-null constraint");
    }

    @Test
    void checkNotNullConstraintMaxYear() {
      final PeriodEntity entity = createValidPeriodEntity();
      entity.setMaxYear(null);

      assertThatThrownBy(() -> periodRepository.save(entity))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining(
              "null value in column \"max_year\" of relation \"period\" violates not-null constraint");
    }

    @Test
    void checkFkChainId() {
      final PeriodEntity entity = createValidPeriodEntity();
      // reference to a not existing chain
      entity.setChainId(UUID.randomUUID());

      assertThatThrownBy(() -> periodRepository.save(entity))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("fk_period_chain");
    }

    @Test
    void finder() {
      final UUID chainId = UUID.randomUUID();
      saveChain(chainId);
      PeriodEntity entityLowerYear = new PeriodEntity();
      entityLowerYear.setChainId(chainId);
      entityLowerYear.setMinYear(2010);
      entityLowerYear.setMaxYear(2011);
      entityLowerYear = periodRepository.save(entityLowerYear);

      PeriodEntity entityHigherYear = new PeriodEntity();
      entityHigherYear.setChainId(chainId);
      entityHigherYear.setMinYear(2020);
      entityHigherYear.setMaxYear(2021);
      entityHigherYear = periodRepository.save(entityHigherYear);

      final PeriodEntity periodOfOtherChain = createValidPeriodEntity();
      periodRepository.save(periodOfOtherChain);

      final List<PeriodEntity> result = periodRepository.findByChainIdOrderByMaxYearDesc(chainId);
      assertThat(result)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactly(entityHigherYear, entityLowerYear);
    }
  }
}
