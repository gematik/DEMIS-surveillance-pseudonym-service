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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.demis.service.base.error.ServiceException;
import de.gematik.demis.surveillance.pseudonym.entity.ChainEntity;
import de.gematik.demis.surveillance.pseudonym.entity.PseudonymChainEntity;
import de.gematik.demis.surveillance.pseudonym.repository.ChainRepository;
import de.gematik.demis.surveillance.pseudonym.repository.PseudonymChainRepository;
import de.gematik.demis.surveillance.pseudonym.service.ChainService.ChainResult;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.util.Pair;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ChainServiceTest {

  private static final byte[] PSEUDONYM_A = new byte[] {1, 2, 3};
  private static final byte[] PSEUDONYM_B = new byte[] {4, 5, 6};
  private static final Pair<byte[], byte[]> TUPLE = Pair.of(PSEUDONYM_A, PSEUDONYM_B);

  @Captor ArgumentCaptor<Collection<byte[]>> findByPseudonymInArgumentCaptor;
  @Captor ArgumentCaptor<Iterable<PseudonymChainEntity>> saveAllArgumentCaptor;
  @Captor ArgumentCaptor<PseudonymChainEntity> saveArgumentCaptor;
  @Captor ArgumentCaptor<ChainEntity> chainArgumentCaptor;

  @Mock private ChainRepository chainRepository;
  @Mock private PseudonymChainRepository pseudoChainRepository;
  @Mock private TransactionTemplate transactionTemplate;
  @InjectMocks private ChainService chainService;

  private static PseudonymChainEntity entity(byte[] pseudonym, UUID chainId, final boolean isNew) {
    final PseudonymChainEntity e = new PseudonymChainEntity();
    e.setPseudonym(pseudonym);
    e.setChainId(chainId);
    e.setNew(isNew);
    return e;
  }

  @Test
  void lookupRightPseudonymsInDatabase() {
    when(pseudoChainRepository.findByPseudonymIn(anyList())).thenReturn(emptyList());

    chainService.chain(TUPLE);

    verify(pseudoChainRepository).findByPseudonymIn(findByPseudonymInArgumentCaptor.capture());
    assertThat(findByPseudonymInArgumentCaptor.getValue())
        .hasSize(2)
        .anyMatch(pseudonym -> Arrays.equals(pseudonym, PSEUDONYM_A))
        .anyMatch(pseudonym -> Arrays.equals(pseudonym, PSEUDONYM_B));
  }

  @Test
  void createNewChainIfNoPseudonymExistsInDatabase() {
    mockTransactionTemplate();
    when(pseudoChainRepository.findByPseudonymIn(anyList())).thenReturn(emptyList());

    final ChainResult result = chainService.chain(TUPLE);

    assertThat(result.newCreated()).isTrue();
    assertThat(result.chainId()).isNotNull();
    verify(chainRepository).save(chainArgumentCaptor.capture());
    verify(pseudoChainRepository).saveAll(saveAllArgumentCaptor.capture());
    assertThat(saveAllArgumentCaptor.getValue())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactlyInAnyOrder(
            entity(PSEUDONYM_A, result.chainId(), true),
            entity(PSEUDONYM_B, result.chainId(), true));
    assertThat(chainArgumentCaptor.getValue())
        .isNotNull()
        .returns(result.chainId(), ChainEntity::getChainId)
        .returns(true, ChainEntity::isNew);
  }

  @Test
  void returnExistingChainIfBothPseudonymsExist() {
    final UUID chainId = UUID.randomUUID();
    final List<PseudonymChainEntity> existing =
        List.of(entity(PSEUDONYM_A, chainId, false), entity(PSEUDONYM_B, chainId, false));
    when(pseudoChainRepository.findByPseudonymIn(anyList())).thenReturn(existing);

    final ChainResult result = chainService.chain(TUPLE);

    assertThat(result.newCreated()).isFalse();
    assertThat(result.chainId()).isEqualTo(chainId);
    verifyNoInteractions(chainRepository);
    verify(pseudoChainRepository, never()).saveAll(any());
    verify(pseudoChainRepository, never()).save(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void returnExistingChainAndAddMissingPseudonymToChainIfOnlyOneExists(final boolean firstExists) {
    final byte[] existingPseudonym = firstExists ? TUPLE.getFirst() : TUPLE.getSecond();
    final byte[] missingPseudonym = firstExists ? TUPLE.getSecond() : TUPLE.getFirst();

    final UUID chainId = UUID.randomUUID();
    final List<PseudonymChainEntity> existing = List.of(entity(existingPseudonym, chainId, false));
    when(pseudoChainRepository.findByPseudonymIn(anyList())).thenReturn(existing);

    final ChainResult result = chainService.chain(TUPLE);

    assertThat(result.newCreated()).isFalse();
    assertThat(result.chainId()).isEqualTo(chainId);
    verify(pseudoChainRepository).save(saveArgumentCaptor.capture());
    assertThat(saveArgumentCaptor.getValue())
        .usingRecursiveComparison()
        .isEqualTo(entity(missingPseudonym, chainId, true));
  }

  @Test
  void parallelSameInputAndAnotherThreadWasFasterCreatingNewChain() {
    final UUID existingChainId = UUID.randomUUID();
    final List<PseudonymChainEntity> reread =
        List.of(
            entity(PSEUDONYM_A, existingChainId, false),
            entity(PSEUDONYM_B, existingChainId, false));
    when(pseudoChainRepository.findByPseudonymIn(anyList()))
        .thenReturn(emptyList())
        .thenReturn(reread);
    when(pseudoChainRepository.saveAll(anyList()))
        .thenThrow(
            new DataIntegrityViolationException(
                "duplicate key value violates unique constraint pk_pseudonym_chain"));
    mockTransactionTemplate();

    final ChainResult result = chainService.chain(TUPLE);

    assertThat(result.chainId()).isEqualTo(existingChainId);
    assertThat(result.newCreated()).isFalse();
  }

  @Test
  void
      throwExceptionIfParallelSameInputAndAnotherThreadWasFasterCreatingNewChainButRereadStillEmptyResult() {
    when(pseudoChainRepository.findByPseudonymIn(anyList()))
        .thenReturn(emptyList()) // first call
        .thenReturn(emptyList()); // reread
    when(pseudoChainRepository.saveAll(anyList())).thenThrow(duplicateKeyError());
    mockTransactionTemplate();

    final ServiceException thrownException =
        catchThrowableOfType(ServiceException.class, () -> chainService.chain(TUPLE));

    verify(pseudoChainRepository, times(2)).findByPseudonymIn(anyList());

    assertThat(thrownException).isNotNull();
    assertThat(thrownException.getErrorCode()).isEqualTo(ErrorCode.CHAIN_INSERT_FAILED.getCode());
    assertThat(thrownException.getCause()).isNotNull();
    assertThat(thrownException.getMessage()).contains("miss 2 pseudonym chain entries");
  }

  @Test
  void throwExceptionIfSavingEntriesFailedDueToAnUnexpectedError() {
    when(pseudoChainRepository.findByPseudonymIn(anyList())).thenReturn(emptyList());
    when(pseudoChainRepository.saveAll(anyList()))
        .thenThrow(new DataIntegrityViolationException("other constraint violated"));
    mockTransactionTemplate();

    final ServiceException thrownException =
        catchThrowableOfType(ServiceException.class, () -> chainService.chain(TUPLE));

    // no duplicate exception -> no reread in this case
    verify(pseudoChainRepository, times(1)).findByPseudonymIn(anyList());

    assertThat(thrownException).isNotNull();
    assertThat(thrownException.getErrorCode()).isEqualTo(ErrorCode.CHAIN_INSERT_FAILED.getCode());
    assertThat(thrownException.getCause()).isInstanceOf(DataIntegrityViolationException.class);
    assertThat(thrownException.getMessage()).contains("Error adding pseudonyms to chain");
  }

  @Test
  void parallelSameInputAndAnotherThreadWasFasterAddingMissingToChain() {
    final UUID existingChainId = UUID.randomUUID();
    final List<PseudonymChainEntity> existing =
        List.of(entity(PSEUDONYM_A, existingChainId, false));
    final List<PseudonymChainEntity> reread = List.of(entity(PSEUDONYM_B, existingChainId, false));
    when(pseudoChainRepository.findByPseudonymIn(anyList()))
        .thenReturn(existing)
        .thenReturn(reread);
    when(pseudoChainRepository.save(any(PseudonymChainEntity.class)))
        .thenThrow(duplicateKeyError());

    final ChainResult result = chainService.chain(TUPLE);

    assertThat(result.chainId()).isEqualTo(existingChainId);
    assertThat(result.newCreated()).isFalse();
  }

  @Test
  void
      throwExceptionIfParallelSameInputAndAnotherThreadWasFasterAddingMissingToChainButWithAnotherChainId() {
    final UUID existingChainId = UUID.randomUUID();
    final UUID otherChainId = UUID.randomUUID();
    final List<PseudonymChainEntity> existing =
        List.of(entity(PSEUDONYM_A, existingChainId, false));
    final List<PseudonymChainEntity> reread = List.of(entity(PSEUDONYM_B, otherChainId, false));
    when(pseudoChainRepository.findByPseudonymIn(anyList()))
        .thenReturn(existing)
        .thenReturn(reread);
    when(pseudoChainRepository.save(any(PseudonymChainEntity.class)))
        .thenThrow(duplicateKeyError());

    final ServiceException thrownException =
        catchThrowableOfType(ServiceException.class, () -> chainService.chain(TUPLE));
    assertThat(thrownException).isNotNull();
    assertThat(thrownException.getErrorCode()).isEqualTo(ErrorCode.CHAIN_INCONSISTENT.getCode());
    assertThat(thrownException.getCause()).isNull();
    assertThat(thrownException.getMessage())
        .contains("missing pseudonym entry was created parallel with another chain id");
  }

  @Test
  void throwExceptionIfChainIdsAreInconsistent() {
    final UUID chainId1 = UUID.randomUUID();
    final UUID chainId2 = UUID.randomUUID();
    final List<PseudonymChainEntity> inconsistent =
        List.of(entity(PSEUDONYM_A, chainId1, false), entity(PSEUDONYM_B, chainId2, false));
    when(pseudoChainRepository.findByPseudonymIn(anyList())).thenReturn(inconsistent);

    final ServiceException thrownException =
        catchThrowableOfType(ServiceException.class, () -> chainService.chain(TUPLE));
    assertThat(thrownException).isNotNull();
    assertThat(thrownException.getErrorCode()).isEqualTo(ErrorCode.CHAIN_INCONSISTENT.getCode());
    assertThat(thrownException.getCause()).isNull();
    assertThat(thrownException.getMessage())
        .contains("For pseudonym tuple different chain ids found.");
  }

  private void mockTransactionTemplate() {
    final TransactionStatus txStatus = Mockito.mock(TransactionStatus.class);
    when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              final TransactionCallback<?> transactionCallback =
                  invocation.getArgument(0, TransactionCallback.class);
              return transactionCallback.doInTransaction(txStatus);
            });
  }

  private DataIntegrityViolationException duplicateKeyError() {
    return new DataIntegrityViolationException(
        "duplicate key value violates unique constraint pk_pseudonym_chain");
  }
}
