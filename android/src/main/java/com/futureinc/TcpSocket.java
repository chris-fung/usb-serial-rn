package com.futureinc.usbserial;

public class TcpSocket {
    private final int id;

    public TcpSocket(final int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
