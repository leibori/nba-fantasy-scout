package com.ori_leibo.nbafantasyscout.client;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import java.util.List;

/** Source of NBA box scores. ESPN today; swappable for another API or a CSV dataset. */
public interface NbaDataClient {

    /** Regular season games of one player; {@code season} is the year the season ends (2026 = 2025-26). */
    List<GameLog> fetchGameLogs(long playerId, int season);
}
