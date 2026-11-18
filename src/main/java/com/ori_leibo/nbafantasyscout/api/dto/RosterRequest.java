package com.ori_leibo.nbafantasyscout.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The user's roster as typed names, and optionally the league's free agents; without them every NBA player
 * not on the roster counts as available. The caps keep a request from matching thousands of names.
 */
public record RosterRequest(@NotEmpty @Size(max = 30) List<String> myRoster, @Size(max = 600) List<String> freeAgents) {

    public List<String> freeAgentsOrEmpty() {
        return freeAgents == null ? List.of() : freeAgents;
    }
}
