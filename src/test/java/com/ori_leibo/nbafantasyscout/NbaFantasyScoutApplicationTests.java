package com.ori_leibo.nbafantasyscout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
// no scheduled sync: it would call ESPN if the test ran at the scheduled minute
@SpringBootTest(properties = "scout.sync.cron=-")
class NbaFantasyScoutApplicationTests {

    @Test
    void contextLoads() {
    }

}
