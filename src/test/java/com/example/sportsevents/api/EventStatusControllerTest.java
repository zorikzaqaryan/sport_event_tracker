package com.example.sportsevents.api;

import com.example.sportsevents.api.dto.EventStatusResponse;
import com.example.sportsevents.domain.EventStatus;
import com.example.sportsevents.exception.GlobalExceptionHandler;
import com.example.sportsevents.service.EventStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EventStatusControllerTest {

    @Mock
    private EventStatusService eventStatusService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EventStatusController(eventStatusService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // -----------------------------------------------------------------------------------------
    // Accepted forms per the spec: boolean, "live", "not live" (case-insensitive, trimmed).
    // The response wire format is the @JsonValue form: "live" / "not live" (lowercase).
    // -----------------------------------------------------------------------------------------

    @Test
    void uppercaseLiveStringIsAcceptedAndResponseUsesLowercaseWireFormat() throws Exception {
        stubAccept("1234", EventStatus.LIVE);

        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":"LIVE"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("1234"))
                .andExpect(jsonPath("$.status").value("live"));

        verify(eventStatusService).updateStatus("1234", EventStatus.LIVE);
    }

    @Test
    void lowercaseLiveStringIsAccepted() throws Exception {
        stubAccept("1234", EventStatus.LIVE);

        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":"live"}"""))
                .andExpect(status().isOk());

        verify(eventStatusService).updateStatus("1234", EventStatus.LIVE);
    }

    @Test
    void notLiveWithSpaceIsAccepted() throws Exception {
        stubAccept("1234", EventStatus.NOT_LIVE);

        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":"not live"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("not live"));

        verify(eventStatusService).updateStatus("1234", EventStatus.NOT_LIVE);
    }

    @Test
    void mixedCaseNotLiveIsAccepted() throws Exception {
        stubAccept("1234", EventStatus.NOT_LIVE);

        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":"  Not Live  "}"""))
                .andExpect(status().isOk());

        verify(eventStatusService).updateStatus("1234", EventStatus.NOT_LIVE);
    }

    @Test
    void booleanTrueIsAcceptedAsLive() throws Exception {
        stubAccept("1234", EventStatus.LIVE);

        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":true}"""))
                .andExpect(status().isOk());

        verify(eventStatusService).updateStatus("1234", EventStatus.LIVE);
    }

    @Test
    void booleanFalseIsAcceptedAsNotLive() throws Exception {
        stubAccept("1234", EventStatus.NOT_LIVE);

        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":false}"""))
                .andExpect(status().isOk());

        verify(eventStatusService).updateStatus("1234", EventStatus.NOT_LIVE);
    }

    @Test
    void numericEventIdIsCoercedToString() throws Exception {
        stubAccept("1234", EventStatus.LIVE);

        mockMvc.perform(jsonPost("""
                        {"eventId":1234,"status":"LIVE"}"""))
                .andExpect(status().isOk());

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventStatusService).updateStatus(idCaptor.capture(), eq(EventStatus.LIVE));
        assertThat(idCaptor.getValue()).isEqualTo("1234");
    }

    // -----------------------------------------------------------------------------------------
    // Rejected forms — anything outside the spec must produce HTTP 400 and never reach the service.
    // -----------------------------------------------------------------------------------------

    @Test
    void missingEventIdReturns400() throws Exception {
        mockMvc.perform(jsonPost("""
                        {"status":"LIVE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details[0]").value(containsString("eventId")));
        verifyNoInteractions(eventStatusService);
    }

    @Test
    void blankEventIdReturns400() throws Exception {
        mockMvc.perform(jsonPost("""
                        {"eventId":"   ","status":"LIVE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(containsString("eventId")));
        verifyNoInteractions(eventStatusService);
    }

    @Test
    void missingStatusReturns400() throws Exception {
        mockMvc.perform(jsonPost("""
                        {"eventId":"1234"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(containsString("status")));
        verifyNoInteractions(eventStatusService);
    }

    @Test
    void nullStatusReturns400() throws Exception {
        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":null}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(containsString("status")));
        verifyNoInteractions(eventStatusService);
    }

    @Test
    void emptyStringStatusReturns400() throws Exception {
        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid status value")));
        verifyNoInteractions(eventStatusService);
    }

    @Test
    void invalidStatusStringReturns400WithUsefulMessage() throws Exception {
        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":"PAUSED"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(allOf(
                        containsString("Invalid status value"),
                        containsString("not live")
                )));
        verifyNoInteractions(eventStatusService);
    }

    @Test
    void javaCanonicalNotLiveWithUnderscoreIsRejected() throws Exception {
        // Strict per spec: only "not live" (with space) is accepted, not "NOT_LIVE" / "not_live".
        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":"NOT_LIVE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid status value")));
        verifyNoInteractions(eventStatusService);
    }

    @Test
    void stringTrueIsRejectedBecauseSpecRequiresJsonBoolean() throws Exception {
        // "true" (string) is neither a JSON boolean nor "live" / "not live".
        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":"true"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid status value")));
        verifyNoInteractions(eventStatusService);
    }

    @Test
    void stringFalseIsRejectedBecauseSpecRequiresJsonBoolean() throws Exception {
        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":"false"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid status value")));
        verifyNoInteractions(eventStatusService);
    }

    @Test
    void numericStatusReturns400() throws Exception {
        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid status type")));
        verifyNoInteractions(eventStatusService);
    }

    @Test
    void arrayStatusReturns400() throws Exception {
        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":["LIVE"]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid status type")));
        verifyNoInteractions(eventStatusService);
    }

    @Test
    void objectStatusReturns400() throws Exception {
        mockMvc.perform(jsonPost("""
                        {"eventId":"1234","status":{"value":"LIVE"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid status type")));
        verifyNoInteractions(eventStatusService);
    }

    private RequestBuilder jsonPost(String body) {
        return post("/events/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void stubAccept(String eventId, EventStatus status) {
        when(eventStatusService.updateStatus(eq(eventId), any()))
                .thenReturn(new EventStatusResponse(eventId, status, "ok"));
    }
}
