package de.gematik.demis.surveillance.pseudonym.api;

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

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.demis.service.base.error.rest.ErrorHandlerConfiguration;
import de.gematik.demis.surveillance.pseudonym.service.PseudonymProcessor;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PseudonymController.class)
@Import(ErrorHandlerConfiguration.class)
class PseudonymControllerIntegrationTest {

  private static final String ENDPOINT = "/pseudonym";

  @MockitoBean PseudonymProcessor pseudonymProcessor;
  @Autowired MockMvc mockMvc;

  private static String toJsonBody(
      final String pseudonym1, final String pseudonym2, final String date) {
    return String.format(
        """
        {
          "pseudonym1": "%s",
          "pseudonym2": "%s",
          "date": "%s"
        }
        """,
        pseudonym1 != null ? pseudonym1 : "",
        pseudonym2 != null ? pseudonym2 : "",
        date != null ? date : "");
  }

  @Test
  void success200() throws Exception {
    final var input = new PseudonymInput("a", "b", LocalDate.of(2025, 7, 28));
    final String requestBody =
        toJsonBody(input.pseudonym1(), input.pseudonym2(), input.date().toString());

    final var serviceOutput = new PseudonymOutput("blh", "101");
    when(pseudonymProcessor.createPseudonym(any())).thenReturn(serviceOutput);

    mockMvc
        .perform(post(ENDPOINT).contentType(APPLICATION_JSON_VALUE).content(requestBody))
        .andExpectAll(
            status().isOk(),
            content().contentTypeCompatibleWith(APPLICATION_JSON_VALUE),
            jsonPath("$.system").value(serviceOutput.system()),
            jsonPath("$.value").value(serviceOutput.value()));

    verify(pseudonymProcessor).createPseudonym(input);
  }

  @Test
  void invalidContentType415() throws Exception {
    final String requestBody = toJsonBody("a", "b", "2025-07-28");
    mockMvc
        .perform(post(ENDPOINT).contentType(TEXT_PLAIN_VALUE).content(requestBody))
        .andExpect(status().is(415));

    verifyNoInteractions(pseudonymProcessor);
  }

  @Test
  void emptyBody400() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).contentType(APPLICATION_JSON_VALUE).content(""))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(pseudonymProcessor);
  }

  @ParameterizedTest
  @CsvSource({"a, , 2025-07-28", ", b, 2025-07-28", "a, b, ", "x, y, 28.07.2025"})
  void invalidRequest400(final String pseudonym1, final String pseudonym2, final String date)
      throws Exception {
    final String requestBody = toJsonBody(pseudonym1, pseudonym2, date);

    mockMvc
        .perform(post(ENDPOINT).contentType(APPLICATION_JSON_VALUE).content(requestBody))
        .andExpectAll(
            status().isBadRequest(), content().contentTypeCompatibleWith(APPLICATION_JSON_VALUE));

    verifyNoInteractions(pseudonymProcessor);
  }

  @Test
  void invalidRequest400WithIdenticalPseudonyms() throws Exception {
    final String pseudonym = "samePseudonym";
    final String requestBody = toJsonBody(pseudonym, pseudonym, "2025-07-28");
    mockMvc
        .perform(post(ENDPOINT).contentType(APPLICATION_JSON_VALUE).content(requestBody))
        .andExpectAll(
            status().isBadRequest(), content().contentTypeCompatibleWith(APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest());
  }
}
