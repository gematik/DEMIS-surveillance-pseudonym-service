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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "period",
    indexes = {@Index(name = "idx_period_chain_id", columnList = "chain_id")})
@Getter
@Setter
@ToString
public class PeriodEntity {

  @GeneratedValue(strategy = GenerationType.UUID)
  @Id
  @Column
  private UUID periodId;

  // FK de.gematik.demis.surveillance.pseudonym.entity.ChainEntity.chainId
  // Note: the reference should not be managed by hibernate, therefore just the id
  @Column(nullable = false, updatable = false)
  private UUID chainId;

  @Column(nullable = false)
  @JdbcTypeCode(SqlTypes.SMALLINT)
  private Integer minYear;

  @Column(nullable = false)
  @JdbcTypeCode(SqlTypes.SMALLINT)
  private Integer maxYear;
}
