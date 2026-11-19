package com.ori_leibo.nbafantasyscout.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// the client's own logic is paging: which pages it asks for and when it stops
@ExtendWith(MockitoExtension.class)
class YahooClientTest {

    private static final String BASE = "https://yahoo.test/v2";
    private static final String FULL_PAGE = YahooMapperTest.fixture("free-agents.json"); // 25 players
    private static final String EMPTY_PAGE = "{\"fantasy_content\": {\"league\": [{}, {\"players\": []}]}}";

    @Mock
    private HttpFetcher http;

    @Test
    void stopsAtTheCapWithoutAskingForMore() {
        when(http.get(anyString())).thenReturn(FULL_PAGE);

        assertEquals(50, new YahooClient(http, BASE, 50, new YahooMapper()).freeAgents(246889).size());

        verify(http).get(BASE + "/league/nba.l.246889/players;status=A;sort=AR;start=0;count=25?format=json");
        verify(http).get(BASE + "/league/nba.l.246889/players;status=A;sort=AR;start=25;count=25?format=json");
        verify(http, times(2)).get(anyString());
    }

    @Test
    void stopsEarlyWhenThePoolRunsOut() {
        when(http.get(anyString())).thenReturn(FULL_PAGE, EMPTY_PAGE);

        assertEquals(25, new YahooClient(http, BASE, 150, new YahooMapper()).freeAgents(246889).size());

        verify(http, times(2)).get(anyString());
    }
}
