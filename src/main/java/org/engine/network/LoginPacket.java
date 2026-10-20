package org.engine.network;

public class LoginPacket {
    public String name;
    public int model;
    public LoginPacket(String name, int model) {
        this.name = name;
        this.model=model;
    }
}