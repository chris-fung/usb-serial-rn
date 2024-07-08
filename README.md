# react-native-serialport
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fchris-fung%2Fusb-serial-rn.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fchris-fung%2Fusb-serial-rn?ref=badge_shield)


#### This library is for usb serial port communication on android platform 

### This module uses the [felHR85/UsbSerial](https://github.com/felHR85/UsbSerial) library

### Documents
1. [Download & Installation](https://github.com/melihyarikkaya/react-native-serialport/wiki/Download-&-Installation)  
2. [Auto Connection](https://github.com/melihyarikkaya/react-native-serialport/wiki/Auto-Connection)  
3. [Manual Connection](https://github.com/melihyarikkaya/react-native-serialport/wiki/Manual-Connection)  
4. [Methods](https://github.com/melihyarikkaya/react-native-serialport/wiki/Methods)  
5. [Error Descriptions](https://github.com/melihyarikkaya/react-native-serialport/wiki/Error-Descriptions)  

### Use to write data to port
```javascript
 RNSerialport.writeString("HELLO");
 RNSerialport.writeBase64("SEVMTE8=");
 RNSerialport.writeHexString("48454C4C4F");
```
### DEFAULT DEFINITIONS
| KEY                    | VALUE                                    |
|------------------------|------------------------------------------|
| RETURNED DATA TYPE     | INT ARRAY (Options: INTARRAY, HEXSTRING) |
| BAUND RATE             | 9600                                     |
| AUTO CONNECT BAUD RATE | 9600                                     |
| PORT INTERFACE         | -1                                       |
| DATA BIT               | 8                                        |
| STOP BIT               | 1                                        |
| PARITY                 | NONE                                     |
| FLOW CONTROL           | OFF                                      |
| DRIVER                 | AUTO                                     |

### Java Package Name
 _com.melihyarikkaya.rnserialport_

## License
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fchris-fung%2Fusb-serial-rn.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2Fchris-fung%2Fusb-serial-rn?ref=badge_large)