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

import static de.gematik.demis.surveillance.pseudonym.service.ErrorCode.CHAIN_INCONSISTENT;
import static de.gematik.demis.surveillance.pseudonym.service.ErrorCode.CHAIN_INSERT_FAILED;

import de.gematik.demis.surveillance.pseudonym.entity.ChainEntity;
import de.gematik.demis.surveillance.pseudonym.entity.PseudonymChainEntity;
import de.gematik.demis.surveillance.pseudonym.repository.ChainRepository;
import de.gematik.demis.surveillance.pseudonym.repository.PseudonymChainRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service for managing pseudonym chains across pepper secret rotations.
 *
 * <p>Due to the periodic rotation of the pepper secret used in the client-side hash function,
 * patient pseudonyms change over time. This results in receiving pairs of pseudonyms for each
 * patient - consisting of the previous (old) and current (new) pseudonym.
 *
 * <p>This service is responsible for:
 *
 * <ul>
 *   <li>Building and maintaining pseudonym chains that link all pseudonyms belonging to a single
 *       patient
 *   <li>Ensuring continuity of patient identification across pepper rotations
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
class ChainService {

  private final ChainRepository chainRepository;
  private final PseudonymChainRepository pseudonymChainRepository;
  private final TransactionTemplate transactionTemplate;

  /**
   * Processes a pseudonym pair and manages the corresponding pseudonym chain.
   *
   * <p>This method handles the following scenarios based on the database state of the provided
   * pseudonyms:
   *
   * <ul>
   *   <li><strong>Both pseudonyms unknown:</strong> Creates and returns a new pseudonym chain
   *       containing both pseudonyms. A new chain entry is also created.
   *   <li><strong>One pseudonym unknown:</strong> Adds the new pseudonym to the existing chain and
   *       returns the updated chain
   *   <li><strong>Both pseudonyms exist in same chain:</strong> Returns the existing chain without
   *       modifications
   *   <li><strong>Pseudonyms exist in different chains:</strong> Throws an exception as this
   *       indicates a data integrity violation
   * </ul>
   */
  public ChainResult chain(final Pair<byte[], byte[]> pseudonymTupel) {
    final List<byte[]> pseudonyms = List.of(pseudonymTupel.getFirst(), pseudonymTupel.getSecond());
    final List<PseudonymChainEntity> existingEntries =
        pseudonymChainRepository.findByPseudonymIn(pseudonyms);

    if (existingEntries.isEmpty()) {
      // chain does not exist -> create new one
      return createNewChain(pseudonyms);
    }

    // chain exist
    final UUID chainId = getChainId(existingEntries);

    if (existingEntries.size() == 1) {
      addMissingPseudonymToChain(pseudonymTupel, existingEntries, chainId);
    }

    return new ChainResult(chainId, false);
  }

  private ChainResult createNewChain(final List<byte[]> pseudonyms) {
    final UUID chainId = UUID.randomUUID();

    final List<PseudonymChainEntity> newEntries =
        pseudonyms.stream()
            .map(pseudonym -> createPseudonymChainEntity(pseudonym, chainId))
            .toList();

    final ChainEntity chainEntity = new ChainEntity();
    chainEntity.setChainId(chainId);
    try {
      transactionTemplate.execute(
          def -> {
            chainRepository.save(chainEntity);
            pseudonymChainRepository.saveAll(newEntries);
            return null;
          });
      return new ChainResult(chainId, true);
    } catch (final DataIntegrityViolationException ex) {
      return handleInsertException(ex, pseudonyms);
    }
  }

  private void addMissingPseudonymToChain(
      final Pair<byte[], byte[]> pseudonymTupel,
      final List<PseudonymChainEntity> existingEntries,
      final UUID chainId) {
    final byte[] missingPseudonym =
        Arrays.equals(existingEntries.getFirst().getPseudonym(), pseudonymTupel.getFirst())
            ? pseudonymTupel.getSecond()
            : pseudonymTupel.getFirst();
    final PseudonymChainEntity entry = createPseudonymChainEntity(missingPseudonym, chainId);
    try {
      pseudonymChainRepository.save(entry);
    } catch (final DataIntegrityViolationException ex) {
      final ChainResult dbChain = handleInsertException(ex, List.of(missingPseudonym));
      if (!chainId.equals(dbChain.chainId())) {
        throw CHAIN_INCONSISTENT.exception(
            "missing pseudonym entry was created parallel with another chain id");
      }
    }
  }

  /**
   * if and only if exception is caused by violating the primary key, we reread the chain entries
   * for the given pseudonyms, check consistency and return the chain id from database. Otherwise,
   * an exception is thrown
   */
  private ChainResult handleInsertException(
      final DataIntegrityViolationException ex, final List<byte[]> pseudonyms) {
    if (isCausedByDuplicateKey(ex)) {
      log.info(
          "ignore insert exception: {} -> reread chain entries from database  ", ex.getMessage());
      final List<PseudonymChainEntity> reread =
          pseudonymChainRepository.findByPseudonymIn(pseudonyms);
      if (reread.size() != pseudonyms.size()) {
        throw CHAIN_INSERT_FAILED.exception(
            String.format(
                "After failed adding and reread chain from database: miss %d pseudonym chain entries",
                pseudonyms.size() - reread.size()),
            ex);
      }
      return new ChainResult(getChainId(reread), false);
    }
    throw CHAIN_INSERT_FAILED.exception("Error adding pseudonyms to chain", ex);
  }

  private boolean isCausedByDuplicateKey(final DataIntegrityViolationException ex) {
    return ex.getMessage().contains("pk_pseudonym_chain");
  }

  private UUID getChainId(final List<PseudonymChainEntity> entries) {
    final UUID chainId = entries.getFirst().getChainId();
    final boolean allMatch =
        entries.stream().skip(1).allMatch(entry -> entry.getChainId().equals(chainId));
    if (!allMatch) {
      // should never happen
      throw CHAIN_INCONSISTENT.exception(
          "Inconsistent Database: For pseudonym tuple different chain ids found.");
    }
    return chainId;
  }

  private PseudonymChainEntity createPseudonymChainEntity(
      final byte[] pseudonym, final UUID chainId) {
    final var entity = new PseudonymChainEntity();
    entity.setPseudonym(pseudonym);
    entity.setChainId(chainId);
    return entity;
  }

  public record ChainResult(UUID chainId, boolean newCreated) {}
}
