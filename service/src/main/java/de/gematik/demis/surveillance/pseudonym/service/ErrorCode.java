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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import de.gematik.demis.service.base.error.ServiceException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
enum ErrorCode {
  CHAIN_INCONSISTENT(HttpStatus.INTERNAL_SERVER_ERROR),
  CHAIN_INSERT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
  CHAIN_MISSING(HttpStatus.INTERNAL_SERVER_ERROR),
  ;

  private final HttpStatus httpStatus;

  public String getCode() {
    return name();
  }

  public ServiceException exception(final String message) {
    return exception(message, null);
  }

  public ServiceException exception(final String message, final Throwable cause) {
    return new ServiceException(getHttpStatus(), getCode(), message, cause);
  }
}
