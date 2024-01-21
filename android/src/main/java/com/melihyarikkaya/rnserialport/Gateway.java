package com.melihyarikkaya.rnserialport;

import android.util.Log;

import java.net.InetSocketAddress;
import java.net.Socket;

public class Gateway {
  public static void onSocketData(Integer socketId, byte[] data, RNSerialportModule serialportGateway) {
    TcpSocketClient socketClient = serialportGateway.getTcpClient(socketId);
    Socket socket = socketClient.getSocket();
    String remoteAddress = ((InetSocketAddress) socket.getRemoteSocketAddress()).getAddress().getHostAddress();
    int remotePort = socket.getPort();
    String hexString = Definitions.bytesToHex(data);
    Log.d(RNSerialportModule.TAG, "onSocketData " + remoteAddress + ":" + remotePort + ": " + hexString);

    Integer appBus = 0; // this example passthrough assume only one serial port, so 0 here, you can customize appBus from somewhere in the data received
    String deviceName = serialportGateway.appBus2DeviceName.get(appBus);
    if (deviceName == null) {
      return;
    }
    serialportGateway.deviceName2SocketId.put(deviceName, socketId);
    serialportGateway.writeSerialportBytes(deviceName, data);
  }

  public static void onSerialportData(String deviceName, byte[] data, RNSerialportModule serialportGateway) {
    String hexString = Definitions.bytesToHex(data);
    Log.d(RNSerialportModule.TAG, "onSerialportData " + deviceName + ": " + hexString);

    Integer socketId = serialportGateway.deviceName2SocketId.get(deviceName);
    if (socketId != null) {
      serialportGateway.writeSocketBytes(socketId, data);
    }
  }
}
