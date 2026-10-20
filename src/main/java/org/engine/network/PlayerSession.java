package org.engine.network;

import io.netty.channel.Channel;

public class PlayerSession {
    public int id;
    public String name;
    public float x, y, z, rotY;
    public int health;
    public int gunType;
    public int model;
    public int killcount;
    public int deadcount;
    public Channel channel;
    public boolean isReady;

    public PlayerSession(int id, int model, String name) {
        this.isReady = false;
        this.id = id;
        this.killcount=0;
        this.deadcount=0;
        this.x = 0; this.y = 0; this.z = 0; this.rotY = 0;
        this.health = 100;
        this.gunType = 0;
        this.name=name;
        this.model = model;
    }
}