package com.ori_leibo.nbafantasyscout.persistence;

import com.ori_leibo.nbafantasyscout.domain.Player;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Row of {@code players}. The id is ESPN's, so saving a known player updates it instead of adding a row. */
@Entity
@Table(name = "players")
public class PlayerEntity {

    @Id
    private long id;
    private String name;
    private String team;
    private String position;
    private String status;
    private Short experienceYears;

    protected PlayerEntity() {
        // for JPA
    }

    private PlayerEntity(long id, String name, String team, String position, String status, Short experienceYears) {
        this.id = id;
        this.name = name;
        this.team = team;
        this.position = position;
        this.status = status;
        this.experienceYears = experienceYears;
    }

    public static PlayerEntity from(Player player) {
        // SMALLINT column, so the entity holds a Short (Hibernate validates the type)
        Integer years = player.experienceYears();
        return new PlayerEntity(player.id(), player.name(), player.team(), player.position(), player.status(),
                years == null ? null : years.shortValue());
    }

    public Player toPlayer() {
        return new Player(id, name, team, position, status, experienceYears == null ? null : experienceYears.intValue());
    }
}
