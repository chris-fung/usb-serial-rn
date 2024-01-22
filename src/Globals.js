import { NativeEventEmitter, NativeModules, Platform } from 'react-native';
const Sockets = Platform.OS === 'android' ? NativeModules.RNSerialport : {};

let instanceNumber = 0;

function getNextId() {
    return instanceNumber++;
}

const nativeEventEmitter = new NativeEventEmitter(Sockets);

export { nativeEventEmitter, getNextId };
