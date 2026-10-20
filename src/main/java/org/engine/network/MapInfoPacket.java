package org.engine.network;

public class MapInfoPacket {
    public String mapName;
    public String urlDownload;

    public MapInfoPacket(String mapName, String urlDownload) {
        this.mapName = mapName;
        this.urlDownload = urlDownload;
    }
}