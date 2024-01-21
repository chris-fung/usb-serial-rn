package com.melihyarikkaya.rnserialport;

import android.app.PendingIntent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableNativeArray;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Network;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

public class RNSerialportModule extends ReactContextBaseJavaModule implements LifecycleEventListener, TcpReceiverTask.OnDataReceivedListener {
    public static final String TAG = "RNSerialport";
    private static final int N_THREADS = 2;
    private static boolean isNativeGateway = false;
    private static boolean isNativeGatewayJsEventEmitOnSerialportData = false;
    public final ReactApplicationContext mReactContext;
    private final ConcurrentHashMap<Integer, TcpSocket> socketMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Network> mNetworkMap = new ConcurrentHashMap<>();
    private final CurrentNetwork currentNetwork = new CurrentNetwork();
    private final ExecutorService executorService = Executors.newFixedThreadPool(N_THREADS);

  // ref to
  // https://github.com/google/guava/blob/6405852bbf453b14d097b8ec3bcae494334b357d/android/guava/src/com/google/common/primitives/UnsignedBytes.java
  private static int unsignedByteToInt(byte value) {
      return value & 0xFF;
  }

  public RNSerialportModule(ReactApplicationContext reactContext) {
    super(reactContext);
    mReactContext = reactContext;
    fillDriverList();
  }

  @Override
  public String getName() {
    return TAG;
  }

  private final String ACTION_USB_READY = "com.felhr.connectivityservices.USB_READY";
  private final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
  private final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
  private final String ACTION_USB_NOT_SUPPORTED = "com.felhr.usbservice.USB_NOT_SUPPORTED";
  private final String ACTION_NO_USB = "com.felhr.usbservice.NO_USB";
  private final String ACTION_USB_PERMISSION_GRANTED = "com.felhr.usbservice.USB_PERMISSION_GRANTED";
  private final String ACTION_USB_PERMISSION_NOT_GRANTED = "com.felhr.usbservice.USB_PERMISSION_NOT_GRANTED";
  private final String ACTION_USB_DISCONNECTED = "com.felhr.usbservice.USB_DISCONNECTED";
  private final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
  private final String ACTION_USB_NOT_OPENED = "com.melihyarikkaya.rnserialport.USB_NOT_OPENED";
  private final String ACTION_USB_CONNECT = "com.melihyarikkaya.rnserialport.USB_CONNECT";

  private final String EXTRA_USB_DEVICE_NAME = "com.melihyarikkaya.rnserialport.USB_DEVICE_NAME";

  //react-native events
  private final String onErrorEvent              = "onError";
  private final String onConnectedEvent          = "onConnected";
  private final String onDisconnectedEvent       = "onDisconnected";
  private final String onDeviceAttachedEvent     = "onDeviceAttached";
  private final String onDeviceDetachedEvent     = "onDeviceDetached";
  private final String onServiceStarted          = "onServiceStarted";
  private final String onServiceStopped          = "onServiceStopped";
  private final String onReadDataFromPort        = "onReadDataFromPort";
  private final String onUsbPermissionGranted    = "onUsbPermissionGranted";

  //SUPPORTED DRIVER LIST

  private List<String> driverList;

  private UsbManager usbManager;
  public Map<String, UsbSerialDevice> serialPorts = new HashMap<>(); // alias deviceName2SerialPort
  public Map<Integer, String> appBus2DeviceName = new HashMap<>(); // App define which bus id match which deviceName
  public Map<String, Integer> deviceName2SocketId = new HashMap<>();

  //Connection Settings
  private int DATA_BIT     = UsbSerialInterface.DATA_BITS_8;
  private int STOP_BIT     = UsbSerialInterface.STOP_BITS_1;
  private int PARITY       =  UsbSerialInterface.PARITY_NONE;
  private int FLOW_CONTROL = UsbSerialInterface.FLOW_CONTROL_OFF;
  private int BAUD_RATE = 9600;


  private boolean autoConnect = false;
  private String autoConnectDeviceName;
  private int autoConnectBaudRate = 9600;
  private int portInterface = -1;
  private int returnedDataType = Definitions.RETURNED_DATA_TYPE_INTARRAY;
  private String driver = "AUTO";


  private boolean usbServiceStarted = false;

  private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context arg0, Intent arg1) {
      Intent intent;
      switch (arg1.getAction()) {
        case ACTION_USB_CONNECT:
          eventEmit(onConnectedEvent, arg1.getExtras().getString(EXTRA_USB_DEVICE_NAME));
          break;
        case ACTION_USB_DISCONNECTED:
          eventEmit(onDisconnectedEvent, arg1.getExtras().getString(EXTRA_USB_DEVICE_NAME));
          break;
        case ACTION_USB_NOT_SUPPORTED:
          eventEmit(onErrorEvent, createError(Definitions.ERROR_DEVICE_NOT_SUPPORTED, Definitions.ERROR_DEVICE_NOT_SUPPORTED_MESSAGE));
          break;
        case ACTION_USB_NOT_OPENED:
          eventEmit(onErrorEvent, createError(Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT, Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT_MESSAGE));
          break;
        case ACTION_USB_ATTACHED: {
          UsbDevice device = arg1.getExtras().getParcelable(UsbManager.EXTRA_DEVICE);
          String deviceName = device.getDeviceName();
          eventEmit(onDeviceAttachedEvent, deviceName);
          if(autoConnect && chooseFirstDevice()) {
            connectDevice(autoConnectDeviceName, autoConnectBaudRate);
          }
        }
          break;
        case ACTION_USB_DETACHED: {
          UsbDevice device = arg1.getExtras().getParcelable(UsbManager.EXTRA_DEVICE);
          String deviceName = device.getDeviceName();
          eventEmit(onDeviceDetachedEvent, deviceName);
          stopConnection(deviceName);
          serialPorts.remove(deviceName);
          appBus2DeviceName.values().removeIf(deviceName::equals);
        }
          break;
        case ACTION_USB_PERMISSION: {
          UsbDevice device = arg1.getExtras().getParcelable(UsbManager.EXTRA_DEVICE);
          boolean granted = arg1.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
          startConnection(device, granted);
        }
          break;
        case ACTION_USB_PERMISSION_GRANTED:
          eventEmit(onUsbPermissionGranted, null);
          break;
        case ACTION_USB_PERMISSION_NOT_GRANTED:
          eventEmit(onErrorEvent, createError(Definitions.ERROR_USER_DID_NOT_ALLOW_TO_CONNECT, Definitions.ERROR_USER_DID_NOT_ALLOW_TO_CONNECT_MESSAGE));
          break;
      }
    }
  };

  private void eventEmit(String eventName, Object data) {
    try {
      if(mReactContext.hasActiveCatalystInstance()) {
        mReactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, data);
      }
    }
    catch (Exception error) {}
  }

  private WritableMap createError(int code, String message) {
    WritableMap err = Arguments.createMap();
    err.putBoolean("status", false);
    err.putInt("errorCode", code);
    err.putString("errorMessage", message);

    return err;
  }

  private WritableMap createError(String deviceName, int code, String message) {
    WritableMap err = Arguments.createMap();
    err.putString("deviceName", deviceName);
    err.putBoolean("status", false);
    err.putInt("errorCode", code);
    err.putString("errorMessage", message);

    return err;
  }

  private void setFilters() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_USB_PERMISSION_GRANTED);
    filter.addAction(ACTION_NO_USB);
    filter.addAction(ACTION_USB_CONNECT);
    filter.addAction(ACTION_USB_DISCONNECTED);
    filter.addAction(ACTION_USB_NOT_SUPPORTED);
    filter.addAction(ACTION_USB_PERMISSION_NOT_GRANTED);
    filter.addAction(ACTION_USB_PERMISSION);
    filter.addAction(ACTION_USB_ATTACHED);
    filter.addAction(ACTION_USB_DETACHED);
    mReactContext.registerReceiver(mUsbReceiver, filter);
  }

  private void fillDriverList() {
    driverList = new ArrayList<>();
    driverList.add("ftdi");
    driverList.add("cp210x");
    driverList.add("pl2303");
    driverList.add("ch34x");
    driverList.add("cdc");
  }

  /******************************* BEGIN PUBLIC SETTER METHODS **********************************/

  @ReactMethod
  public void setDataBit(int DATA_BIT) {
    this.DATA_BIT = DATA_BIT;
  }
  @ReactMethod
  public void setStopBit(int STOP_BIT) {
    this.STOP_BIT = STOP_BIT;
  }
  @ReactMethod
  public void setParity(int PARITY) {
    this.PARITY = PARITY;
  }
  @ReactMethod
  public void setFlowControl(int FLOW_CONTROL) {
    this.FLOW_CONTROL = FLOW_CONTROL;
  }

  @ReactMethod
  public void loadDefaultConnectionSetting() {
    DATA_BIT     = UsbSerialInterface.DATA_BITS_8;
    STOP_BIT     = UsbSerialInterface.STOP_BITS_1;
    PARITY       =  UsbSerialInterface.PARITY_NONE;
    FLOW_CONTROL = UsbSerialInterface.FLOW_CONTROL_OFF;
  }
  @ReactMethod
  public void setAutoConnect(boolean autoConnect) {
    this.autoConnect = autoConnect;
  }
  @ReactMethod
  public void setAutoConnectBaudRate(int baudRate) {
    this.autoConnectBaudRate = baudRate;
  }
  @ReactMethod
  public void setInterface(int iFace) {
    this.portInterface = iFace;
  }
  @ReactMethod
  public void setReturnedDataType(int type) {
    if(type == Definitions.RETURNED_DATA_TYPE_HEXSTRING || type == Definitions.RETURNED_DATA_TYPE_INTARRAY) {
      this.returnedDataType = type;
    }
  }

  @ReactMethod
  public void setDriver(String driver) {
    if(driver.isEmpty() || !driverList.contains(driver.trim())) {
      eventEmit(onErrorEvent, createError(Definitions.ERROR_DRIVER_TYPE_NOT_FOUND, Definitions.ERROR_DRIVER_TYPE_NOT_FOUND_MESSAGE));
      return;
    }

    this.driver = driver;
  }

  /********************************************* END **********************************************/

  @ReactMethod
  public void startUsbService() {
    if(usbServiceStarted) {
      return;
    }
    setFilters();

    usbManager = (UsbManager) mReactContext.getSystemService(Context.USB_SERVICE);

    usbServiceStarted = true;

    //Return usb status when service is started.
    WritableMap map = Arguments.createMap();

    map.putBoolean("deviceAttached", !usbManager.getDeviceList().isEmpty());

    eventEmit(onServiceStarted, map);

    checkAutoConnect();
  }

  @ReactMethod
  public void stopUsbService() {
    if(!serialPorts.isEmpty()) {
      eventEmit(onErrorEvent, createError(Definitions.ERROR_SERVICE_STOP_FAILED, Definitions.ERROR_SERVICE_STOP_FAILED_MESSAGE));
      return;
    }
    if(!usbServiceStarted) {
      return;
    }
    mReactContext.unregisterReceiver(mUsbReceiver);
    usbServiceStarted = false;
    eventEmit(onServiceStopped, null);
  }

  @Override
  public void onHostResume() {}

  @Override
  public void onHostPause() {}

  @Override
  public void onHostDestroy() {}

  @Override
  public void onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy();
    disconnectAllDevices();
    stopUsbService();
  }

  @ReactMethod
  public void getDeviceList(Promise promise) {
    if(!usbServiceStarted) {
      promise.reject(String.valueOf(Definitions.ERROR_USB_SERVICE_NOT_STARTED), Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE);
      return;
    }

    UsbManager manager = (UsbManager) mReactContext.getSystemService(Context.USB_SERVICE);

    HashMap<String, UsbDevice> devices = manager.getDeviceList();

    if(devices.isEmpty()) {
      //promise.reject(String.valueOf(Definitions.ERROR_DEVICE_NOT_FOUND), Definitions.ERROR_DEVICE_NOT_FOUND_MESSAGE);
      promise.resolve(Arguments.createArray());
      return;
    }

    WritableArray deviceList = Arguments.createArray();
    for(Map.Entry<String, UsbDevice> entry: devices.entrySet()) {
      UsbDevice d = entry.getValue();

      WritableMap map = Arguments.createMap();
      map.putString("name", d.getDeviceName());
      map.putInt("vendorId", d.getVendorId());
      map.putInt("productId", d.getProductId());

      deviceList.pushMap(map);
    }

    promise.resolve(deviceList);
  }

  @ReactMethod
  public void connectDevice(String deviceName, int baudRate) {
    try {
      if(!usbServiceStarted){
        eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
        return;
      }

      if(deviceName.isEmpty() || deviceName.length() < 0) {
        eventEmit(onErrorEvent, createError(Definitions.ERROR_CONNECT_DEVICE_NAME_INVALID, Definitions.ERROR_CONNECT_DEVICE_NAME_INVALID_MESSAGE));
        return;
      }

      if(serialPorts.get(deviceName) != null) {
        eventEmit(onErrorEvent, createError(deviceName, Definitions.ERROR_SERIALPORT_ALREADY_CONNECTED, Definitions.ERROR_SERIALPORT_ALREADY_CONNECTED_MESSAGE));
        return;
      }

      if(baudRate < 1){
        eventEmit(onErrorEvent, createError(deviceName, Definitions.ERROR_CONNECT_BAUDRATE_EMPTY, Definitions.ERROR_CONNECT_BAUDRATE_EMPTY_MESSAGE));
        return;
      }

      if(!autoConnect) {
        this.BAUD_RATE = baudRate;
      }

      UsbDevice device = chooseDevice(deviceName);

      if(device == null) {
        eventEmit(onErrorEvent, createError(deviceName, Definitions.ERROR_X_DEVICE_NOT_FOUND, Definitions.ERROR_X_DEVICE_NOT_FOUND_MESSAGE + deviceName));
        return;
      }

      if (usbManager.hasPermission(device)) {
        startConnection(device, true);
      } else {
        requestUserPermission(device);
      }

    } catch (Exception err) {
      eventEmit(onErrorEvent, createError(deviceName, Definitions.ERROR_CONNECTION_FAILED, Definitions.ERROR_CONNECTION_FAILED_MESSAGE + " Catch Error Message:" + err.getMessage()));
    }
  }

  @ReactMethod
  public void disconnectDevice(String deviceName) {
    if(!usbServiceStarted){
      eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
      return;
    }

    if(!serialPorts.containsKey(deviceName)) {
      eventEmit(onErrorEvent, createError(Definitions.ERROR_SERIALPORT_ALREADY_DISCONNECTED, Definitions.ERROR_SERIALPORT_ALREADY_DISCONNECTED_MESSAGE));
      return;
    }
    stopConnection(deviceName);
    serialPorts.remove(deviceName);
  }

  @ReactMethod
  public void disconnectAllDevices() {
    if(!usbServiceStarted){
      eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
      return;
    }

    // Intent intent = new Intent(ACTION_USB_DETACHED);
    // mReactContext.sendBroadcast(intent);

    // above will cause
    // `Permission Denial: not allowed to send broadcast android.hardware.usb.action.USB_DEVICE_DETACHED from pid=6037, uid=10068`
    // so use below instead

    for(Map.Entry<String, UsbSerialDevice> entry: serialPorts.entrySet()) {
      stopConnection(entry.getKey());
    }
    serialPorts.clear();
    appBus2DeviceName.clear();
  }

  @ReactMethod
  public void isOpen(String deviceName, Promise promise) {
    promise.resolve(serialPorts.containsKey(deviceName));
  }

 @ReactMethod
 public void isServiceStarted(Promise promise) {
    promise.resolve(usbServiceStarted);
 }

  @ReactMethod
  public void isSupported(String deviceName, Promise promise) {
    UsbDevice device = chooseDevice(deviceName);

    if(device == null) {
      promise.reject(String.valueOf(Definitions.ERROR_DEVICE_NOT_FOUND), Definitions.ERROR_DEVICE_NOT_FOUND_MESSAGE);
    } else {
      promise.resolve(UsbSerialDevice.isSupported(device));
    }
  }

  public void writeSerialportBytes(String deviceName, byte[] bytes) {
    if(!usbServiceStarted){
      eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
      return;
    }
    UsbSerialDevice serialPort = serialPorts.get(deviceName);
    if(serialPort == null) {
      eventEmit(onErrorEvent, createError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE));
      return;
    }
    serialPort.write(bytes);
  }

  @ReactMethod
  public void writeBytes(String deviceName, ReadableArray message) {
    if(!usbServiceStarted){
      eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
      return;
    }
    UsbSerialDevice serialPort = serialPorts.get(deviceName);
    if(serialPort == null) {
      eventEmit(onErrorEvent, createError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE));
      return;
    }
    int length = message.size();
    byte [] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = (byte)message.getInt(i);
    }
    serialPort.write(bytes);
  }

  @ReactMethod
  public void writeString(String deviceName, String message) {
    if(!usbServiceStarted){
      eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
      return;
    }
    UsbSerialDevice serialPort = serialPorts.get(deviceName);
    if(serialPort == null) {
      eventEmit(onErrorEvent, createError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE));
      return;
    }

    serialPort.write(message.getBytes());
  }

  @ReactMethod
  public void writeBase64(String deviceName, String message) {
    if(!usbServiceStarted){
      eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
      return;
    }
    UsbSerialDevice serialPort = serialPorts.get(deviceName);
    if(serialPort == null) {
      eventEmit(onErrorEvent, createError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE));
      return;
    }

    byte [] data = Base64.decode(message, Base64.DEFAULT);
    serialPort.write(data);
  }

  @ReactMethod
  public void writeHexString(String deviceName, String message) {
    if(!usbServiceStarted){
      eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
      return;
    }
    UsbSerialDevice serialPort = serialPorts.get(deviceName);
    if(serialPort == null) {
      eventEmit(onErrorEvent, createError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE));
      return;
    }

    String msg = message.toUpperCase();

    if(msg.length() < 1) {
      return;
    }

    byte[] data = new byte[msg.length() / 2];
    for (int i = 0; i < data.length; i++) {
      int index = i * 2;

      String hex = msg.substring(index, index + 2);

      if(Definitions.hexChars.indexOf(hex.substring(0, 1)) == -1 || Definitions.hexChars.indexOf(hex.substring(1, 1)) == -1) {
          return;
      }

      int v = Integer.parseInt(hex, 16);
      data[i] = (byte) v;
    }
    serialPort.write(data);
  }

  ///////////////////////////////////////////////USB SERVICE /////////////////////////////////////////////////////////
  ///////////////////////////////////////////////USB SERVICE /////////////////////////////////////////////////////////

  private UsbDevice chooseDevice(String deviceName) {
    HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
    if(usbDevices.isEmpty()) {
      return null;
    }

    UsbDevice device = null;

    for (Map.Entry<String, UsbDevice> entry: usbDevices.entrySet()) {
      UsbDevice d = entry.getValue();

      if(d.getDeviceName().equals(deviceName)) {
        device = d;
        break;
      }
    }

    return device;
  }

  private boolean chooseFirstDevice() {
    HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
    if(usbDevices.isEmpty()) {
      return false;
    }

    boolean selected = false;

    for (Map.Entry<String, UsbDevice> entry: usbDevices.entrySet()) {
      UsbDevice d = entry.getValue();

      int deviceVID = d.getVendorId();
      int devicePID = d.getProductId();

      if (deviceVID != 0x1d6b && (devicePID != 0x0001 && devicePID != 0x0002 && devicePID != 0x0003) && deviceVID != 0x5c6 && devicePID != 0x904c)
      {
        autoConnectDeviceName = d.getDeviceName();
        selected = true;
        break;
      }
    }
    return selected;
  }

  private void checkAutoConnect() {
    if(!autoConnect || !serialPorts.isEmpty())
      return;

    if(chooseFirstDevice()) {
      connectDevice(autoConnectDeviceName, autoConnectBaudRate);
    }
  }
  private class ConnectionThread extends Thread {
    private UsbDevice device;
    private UsbDeviceConnection connection;

    public ConnectionThread(UsbDevice device, UsbDeviceConnection connection) {
        this.device = device;
        this.connection = connection;
    }

    @Override
    public void run() {
      try {
        UsbSerialDevice serialPort;
        if(driver.equals("AUTO")) {
          serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection, portInterface);
        } else {
          serialPort = UsbSerialDevice.createUsbSerialDevice(driver, device, connection, portInterface);
        }
        if(serialPort == null) {
          // No driver for given device
          Intent intent = new Intent(ACTION_USB_NOT_SUPPORTED);
          mReactContext.sendBroadcast(intent);
          return;
        }

        if(!serialPort.open()){
          Intent intent = new Intent(ACTION_USB_NOT_OPENED);
          mReactContext.sendBroadcast(intent);
          return;
        }

        serialPorts.put(device.getDeviceName(), serialPort);
        int baud;
        if(autoConnect){
          baud = autoConnectBaudRate;
        }else {
          baud = BAUD_RATE;
        }
        serialPort.setBaudRate(baud);
        serialPort.setDataBits(DATA_BIT);
        serialPort.setStopBits(STOP_BIT);
        serialPort.setParity(PARITY);
        serialPort.setFlowControl(FLOW_CONTROL);

        UsbSerialInterface.UsbReadCallback usbReadCallback = new UsbSerialInterface.UsbReadCallback() {
          @Override
          public void onReceivedData(byte[] bytes) {
            if (bytes.length == 0) {
              // onCatalystInstanceDestroy will cause here
              return;
            }

            if (isNativeGateway) {
              Gateway.onSerialportData(device.getDeviceName(), bytes, RNSerialportModule.this);
              if (!isNativeGatewayJsEventEmitOnSerialportData) {
                return;
              }
            }

            try {

              String payloadKey = "payload";

              WritableMap params = Arguments.createMap();

              if(returnedDataType == Definitions.RETURNED_DATA_TYPE_INTARRAY) {
                WritableArray intArray = new WritableNativeArray();
                for(byte b: bytes) {
                  intArray.pushInt(unsignedByteToInt(b));
                }
                params.putArray(payloadKey, intArray);
              } else if(returnedDataType == Definitions.RETURNED_DATA_TYPE_HEXSTRING) {
                String hexString = Definitions.bytesToHex(bytes);
                params.putString(payloadKey, hexString);
              } else {
                return;
              }

              params.putString("deviceName", device.getDeviceName());

              eventEmit(onReadDataFromPort, params);
            } catch (Exception err) {
              eventEmit(onErrorEvent, createError(Definitions.ERROR_NOT_READED_DATA, Definitions.ERROR_NOT_READED_DATA_MESSAGE + " System Message: " + err.getMessage()));
            }
          }
        };
        serialPort.read(usbReadCallback);

        Intent intent = new Intent(ACTION_USB_READY);
        mReactContext.sendBroadcast(intent);
        intent = new Intent(ACTION_USB_CONNECT);
        intent.putExtra(EXTRA_USB_DEVICE_NAME, device.getDeviceName());
        mReactContext.sendBroadcast(intent);
      } catch (Exception error) {
        WritableMap map = createError(Definitions.ERROR_CONNECTION_FAILED, Definitions.ERROR_CONNECTION_FAILED_MESSAGE);
        map.putString("exceptionErrorMessage", error.getMessage());
        eventEmit(onErrorEvent, map);
      }
    }
  }

  private void requestUserPermission(UsbDevice device) {
    if(device == null)
      return;
    PendingIntent mPendingIntent = PendingIntent.getBroadcast(mReactContext, 0 , new Intent(ACTION_USB_PERMISSION), 0);
    usbManager.requestPermission(device, mPendingIntent);
  }

  private void startConnection(UsbDevice device, boolean granted) {
    if(granted) {
      Intent intent = new Intent(ACTION_USB_PERMISSION_GRANTED);
      mReactContext.sendBroadcast(intent);
      UsbDeviceConnection connection = usbManager.openDevice(device);
      new ConnectionThread(device, connection).start();
    } else {
      Intent intent = new Intent(ACTION_USB_PERMISSION_NOT_GRANTED);
      mReactContext.sendBroadcast(intent);
    }
  }

  private void stopConnection(String deviceName) {
    UsbSerialDevice serialPort = serialPorts.get(deviceName);
    if(serialPort == null) {
      eventEmit(onErrorEvent, createError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE));
      return;
    }

    serialPort.close();
    appBus2DeviceName.values().removeIf(deviceName::equals);

    Intent intent = new Intent(ACTION_USB_DISCONNECTED);
    intent.putExtra(EXTRA_USB_DEVICE_NAME, deviceName);
    mReactContext.sendBroadcast(intent);
  }

  ///////////////////////////////////////////////TCP Socket /////////////////////////////////////////////////////////
  ///////////////////////////////////////////////TCP Socket /////////////////////////////////////////////////////////

    private void sendEvent(String eventName, WritableMap params) {
        mReactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    /**
     * Creates a TCP Socket and establish a connection with the given host
     *
     * @param cId     socket ID
     * @param host    socket IP address
     * @param port    socket port to be bound
     * @param options extra options
     */
    @SuppressLint("StaticFieldLeak")
    @SuppressWarnings("unused")
    @ReactMethod
    public void connect(@NonNull final Integer cId, @NonNull final String host, @NonNull final Integer port, @NonNull final ReadableMap options) {
        executorService.execute(new Thread(new Runnable() {
            @Override
            public void run() {
                if (socketMap.get(cId) != null) {
                    onError(cId, TAG + "createSocket called twice with the same id.");
                    return;
                }
                try {
                    // Get the network interface
                    final String localAddress = options.hasKey("localAddress") ? options.getString("localAddress") : null;
                    final String iface = options.hasKey("interface") ? options.getString("interface") : null;
                    selectNetwork(iface, localAddress);
                    TcpSocketClient client = new TcpSocketClient(RNSerialportModule.this, cId, null);
                    socketMap.put(cId, client);
                    client.connect(mReactContext, host, port, options, currentNetwork.getNetwork());
                    onConnect(cId, client);
                } catch (Exception e) {
                    onError(cId, e.getMessage());
                }
            }
        }));
    }

    public void writeSocketBytes(@NonNull final Integer cId, @NonNull final byte[] bytes) {
        TcpSocketClient socketClient = getTcpClient(cId);
        try {
            socketClient.write(bytes);
        } catch (IOException e) {
            onError(cId, e.toString());
        }
    }

    @SuppressLint("StaticFieldLeak")
    @SuppressWarnings("unused")
    @ReactMethod
    public void write(@NonNull final Integer cId, @NonNull final String base64String, @Nullable final Callback callback) {
        executorService.execute(new Thread(new Runnable() {
            @Override
            public void run() {
                TcpSocketClient socketClient = getTcpClient(cId);
                try {
                    socketClient.write(Base64.decode(base64String, Base64.NO_WRAP));
                    if (callback != null) {
                        callback.invoke();
                    }
                } catch (IOException e) {
                    if (callback != null) {
                        callback.invoke(e.toString());
                    }
                    onError(cId, e.toString());
                }
            }
        }));
    }

    @SuppressLint("StaticFieldLeak")
    @SuppressWarnings("unused")
    @ReactMethod
    public void end(final Integer cId) {
        executorService.execute(new Thread(new Runnable() {
            @Override
            public void run() {
                TcpSocketClient socketClient = getTcpClient(cId);
                socketClient.destroy();
                socketMap.remove(cId);
                deviceName2SocketId.values().removeIf(cId::equals);
            }
        }));
    }

    @SuppressWarnings("unused")
    @ReactMethod
    public void destroy(final Integer cId) {
        end(cId);
    }

    @SuppressWarnings("unused")
    @ReactMethod
    public void close(final Integer cId) {
        executorService.execute(new Thread(new Runnable() {
            @Override
            public void run() {
                TcpSocketServer socketServer = getTcpServer(cId);
                socketServer.close();
                socketMap.remove(cId);
                deviceName2SocketId.values().removeIf(cId::equals);
            }
        }));
    }

    @SuppressLint("StaticFieldLeak")
    @SuppressWarnings("unused")
    @ReactMethod
    public void listen(final Integer cId, final ReadableMap options) {
        executorService.execute(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TcpSocketServer server = new TcpSocketServer(socketMap, RNSerialportModule.this, cId, options);
                    socketMap.put(cId, server);
                    onListen(cId, server);
                } catch (Exception uhe) {
                    onError(cId, uhe.getMessage());
                }
            }
        }));
    }

    @SuppressWarnings("unused")
    @ReactMethod
    public void setNoDelay(@NonNull final Integer cId, final boolean noDelay) {
        final TcpSocketClient client = getTcpClient(cId);
        try {
            client.setNoDelay(noDelay);
        } catch (IOException e) {
            onError(cId, e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    @ReactMethod
    public void setKeepAlive(@NonNull final Integer cId, final boolean enable, final int initialDelay) {
        final TcpSocketClient client = getTcpClient(cId);
        try {
            client.setKeepAlive(enable, initialDelay);
        } catch (IOException e) {
            onError(cId, e.getMessage());
        }
    }

    private void requestNetwork(final int transportType) throws InterruptedException {
        final NetworkRequest.Builder requestBuilder = new NetworkRequest.Builder();
        requestBuilder.addTransportType(transportType);
        final CountDownLatch awaitingNetwork = new CountDownLatch(1); // only needs to be counted down once to release waiting threads
        final ConnectivityManager cm = (ConnectivityManager) mReactContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.requestNetwork(requestBuilder.build(), new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                currentNetwork.setNetwork(network);
                awaitingNetwork.countDown(); // Stop waiting
            }

            @Override
            public void onUnavailable() {
                awaitingNetwork.countDown(); // Stop waiting
            }
        });
        // Timeout if there the network is unreachable
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
        exec.schedule(new Runnable() {
            public void run() {
                awaitingNetwork.countDown(); // Stop waiting
            }
        }, 5, TimeUnit.SECONDS);
        awaitingNetwork.await();
    }

    // REQUEST NETWORK

    /**
     * Returns a network given its interface name:
     * "wifi" -> WIFI
     * "cellular" -> Cellular
     * etc...
     */
    private void selectNetwork(@Nullable final String iface, @Nullable final String ipAddress) throws InterruptedException, IOException {
        currentNetwork.setNetwork(null);
        if (iface == null) return;
        if (ipAddress != null) {
            final Network cachedNetwork = mNetworkMap.get(iface + ipAddress);
            if (cachedNetwork != null) {
                currentNetwork.setNetwork(cachedNetwork);
                return;
            }
        }
        switch (iface) {
            case "wifi":
                requestNetwork(NetworkCapabilities.TRANSPORT_WIFI);
                break;
            case "cellular":
                requestNetwork(NetworkCapabilities.TRANSPORT_CELLULAR);
                break;
            case "ethernet":
                requestNetwork(NetworkCapabilities.TRANSPORT_ETHERNET);
                break;
        }
        if (currentNetwork.getNetwork() == null) {
            throw new IOException("Interface " + iface + " unreachable");
        } else if (ipAddress != null && !ipAddress.equals("0.0.0.0"))
            mNetworkMap.put(iface + ipAddress, currentNetwork.getNetwork());
    }

    // TcpReceiverTask.OnDataReceivedListener

    @Override
    public void onConnect(Integer id, TcpSocketClient client) {
        WritableMap eventParams = Arguments.createMap();
        eventParams.putInt("id", id);
        WritableMap connectionParams = Arguments.createMap();
        final Socket socket = client.getSocket();
        final InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();

        connectionParams.putString("localAddress", socket.getLocalAddress().getHostAddress());
        connectionParams.putInt("localPort", socket.getLocalPort());
        connectionParams.putString("remoteAddress", remoteAddress.getAddress().getHostAddress());
        connectionParams.putInt("remotePort", socket.getPort());
        connectionParams.putString("remoteFamily", remoteAddress.getAddress() instanceof Inet6Address ? "IPv6" : "IPv4");
        eventParams.putMap("connection", connectionParams);
        sendEvent("connect", eventParams);
    }

    @Override
    public void onListen(Integer id, TcpSocketServer server) {
        WritableMap eventParams = Arguments.createMap();
        eventParams.putInt("id", id);
        WritableMap connectionParams = Arguments.createMap();
        final ServerSocket serverSocket = server.getServerSocket();
        final InetAddress address = serverSocket.getInetAddress();

        connectionParams.putString("localAddress", serverSocket.getInetAddress().getHostAddress());
        connectionParams.putInt("localPort", serverSocket.getLocalPort());
        connectionParams.putString("localFamily", address instanceof Inet6Address ? "IPv6" : "IPv4");
        eventParams.putMap("connection", connectionParams);
        sendEvent("listening", eventParams);
    }

    @Override
    public void onData(Integer id, byte[] data) {
        if (isNativeGateway) {
            Gateway.onSocketData(id, data, RNSerialportModule.this);
            return;
        }

        WritableMap eventParams = Arguments.createMap();
        eventParams.putInt("id", id);
        eventParams.putString("data", Base64.encodeToString(data, Base64.NO_WRAP));

        sendEvent("data", eventParams);
    }

    @Override
    public void onClose(Integer id, String error) {
        socketMap.remove(id);
        deviceName2SocketId.values().removeIf(id::equals);

        if (error != null) {
            onError(id, error);
        }
        WritableMap eventParams = Arguments.createMap();
        eventParams.putInt("id", id);
        eventParams.putBoolean("hadError", error != null);

        sendEvent("close", eventParams);
    }

    @Override
    public void onError(Integer id, String error) {
        WritableMap eventParams = Arguments.createMap();
        eventParams.putInt("id", id);
        eventParams.putString("error", error);

        sendEvent("error", eventParams);
    }

    @Override
    public void onConnection(Integer serverId, Integer clientId, Socket socket) {
        WritableMap eventParams = Arguments.createMap();
        eventParams.putInt("id", serverId);

        WritableMap infoParams = Arguments.createMap();
        infoParams.putInt("id", clientId);

        WritableMap connectionParams = Arguments.createMap();
        final InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();

        connectionParams.putString("localAddress", socket.getLocalAddress().getHostAddress());
        connectionParams.putInt("localPort", socket.getLocalPort());
        connectionParams.putString("remoteAddress", remoteAddress.getAddress().getHostAddress());
        connectionParams.putInt("remotePort", socket.getPort());
        connectionParams.putString("remoteFamily", remoteAddress.getAddress() instanceof Inet6Address ? "IPv6" : "IPv4");

        infoParams.putMap("connection", connectionParams);
        eventParams.putMap("info", infoParams);

        sendEvent("connection", eventParams);
    }

    public TcpSocketClient getTcpClient(final int id) {
        TcpSocket socket = socketMap.get(id);
        if (socket == null) {
            throw new IllegalArgumentException(TAG + "No socket with id " + id);
        }
        if (!(socket instanceof TcpSocketClient)) {
            throw new IllegalArgumentException(TAG + "Socket with id " + id + " is not a client");
        }
        return (TcpSocketClient) socket;
    }

    private TcpSocketServer getTcpServer(final int id) {
        TcpSocket socket = socketMap.get(id);
        if (socket == null) {
            throw new IllegalArgumentException(TAG + "No socket with id " + id);
        }
        if (!(socket instanceof TcpSocketServer)) {
            throw new IllegalArgumentException(TAG + "Socket with id " + id + " is not a server");
        }
        return (TcpSocketServer) socket;
    }

    private static class CurrentNetwork {
        @Nullable
        Network network = null;

        private CurrentNetwork() {
        }

        @Nullable
        private Network getNetwork() {
            return network;
        }

        private void setNetwork(@Nullable final Network network) {
            this.network = network;
        }
    }

  ///////////////////////////////////////////////Gateway /////////////////////////////////////////////////////////
  ///////////////////////////////////////////////Gateway /////////////////////////////////////////////////////////

  @ReactMethod
  public void setIsNativeGateway(final boolean isNativeGw) {
    isNativeGateway = isNativeGw;
  }

  @ReactMethod
  public void setIsNativeGatewayJsEventEmitOnSerialportData(final boolean isJsEvent) {
    isNativeGatewayJsEventEmitOnSerialportData = isJsEvent;
  }

  @ReactMethod
  public void appBus2DeviceNamePut(Integer appBus, String deviceName) {
    appBus2DeviceName.put(appBus, deviceName);
  }
}
