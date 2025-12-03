package de.gematik.demis.surveillance.pseudonym.entity;

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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/** This entity is just for pessimistic locking */
@Entity
@Table(name = "chain")
@Immutable
@Getter
@Setter
@ToString
public class ChainEntity implements Persistable<UUID> {

  @Id @Column private UUID chainId;

  @Column(insertable = false, updatable = false, nullable = false)
  private Instant createdAt;

  @Transient private boolean isNew = true;

  @Override
  public UUID getId() {
    return getChainId();
  }

  /**
   * Indicates if this entity is new to avoid unnecessary SELECT queries.
   *
   * @see PseudonymChainEntity#isNew()
   */
  @Override
  public boolean isNew() {
    return isNew;
  }

  // Lifecycle-Callback: After reading / saving the entity is not new anymore
  @PostLoad
  @PostPersist
  private void markNotNew() {
    this.isNew = false;
  }
}
