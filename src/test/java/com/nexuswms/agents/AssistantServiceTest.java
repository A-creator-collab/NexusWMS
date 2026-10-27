package com.nexuswms.agents;

import com.nexuswms.ai.*;
import com.nexuswms.service.*;
import com.nexuswms.voice.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssistantServiceTest {
    LlmClient llm = mock(LlmClient.class);
    CommandExecutor executor = mock(CommandExecutor.class);
    AnalyticsService analytics = mock(AnalyticsService.class);
    AssistantService svc;
    List<String> chunks = new ArrayList<>();

    @BeforeEach void setUp() {
        svc = new AssistantService(llm, new CommandParser(), executor, analytics, mock(WarehouseTools.class));
        when(executor.execute(any(), any(), anyBoolean())).thenReturn("local-answer");
        when(analytics.kpis()).thenReturn(new AnalyticsService.Kpis(10, 2, 1, 3));
    }

    @Test void recognisedCommandsNeverHitTheModel() throws Exception {
        assertEquals("local-answer", svc.ask("show low stock", 1L, true, chunks::add));
        verify(llm, never()).chat(any(), any(), any(), any(), any());
    }
    @Test void freeFormQuestionsGoToTheModelAndAreRemembered() throws Exception {
        when(llm.available()).thenReturn(true);
        when(llm.chat(any(), any(), eq("why is stock dropping?"), any(), any())).thenAnswer(i -> { ((java.util.function.Consumer<String>) i.getArgument(4)).accept("because"); return "because demand"; });
        assertEquals("because demand", svc.ask("why is stock dropping?", 1L, false, chunks::add));
        assertEquals(List.of("because"), chunks);
        svc.ask("and tomorrow?", 1L, false, chunks::add);
        verify(llm).chat(any(), argThat(h -> h.size() == 2), eq("and tomorrow?"), any(), any());     // previous turn passed as history
    }
    @Test void invalidKeyGivesClearMessagePlusLocalAnswer() throws Exception {
        when(llm.available()).thenReturn(true);
        when(llm.chat(any(), any(), any(), any(), any())).thenThrow(new LlmException(LlmException.Kind.AUTH, "401"));
        String out = svc.ask("summary of everything", 1L, false, chunks::add);
        assertTrue(out.contains("rejected the API key")); assertTrue(out.contains("SKUs: 10"));
    }
    @Test void aiOffUsesRuleBasedAnswers() {
        when(llm.available()).thenReturn(false);
        assertTrue(svc.ask("give me a summary", 1L, false, chunks::add).contains("open POs: 3"));
    }
    @Test void blankQuestionIsHandled() { assertFalse(svc.ask("   ", 1L, false, chunks::add).isBlank()); assertNull(null); }
}
