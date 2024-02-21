package com.smartpos.demo.jscallprint;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.cloudpos.DeviceException;
import com.cloudpos.POSTerminal;
import com.cloudpos.printer.PrinterDevice;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class JsCallPrintInterface {
    private MainActivity mContext;
    private PrinterDevice device;

    public JsCallPrintInterface(MainActivity mContext) {
        this.mContext = mContext;
    }

    @android.webkit.JavascriptInterface
    public void javaSDKDevicePrint(String text) {
        printTextBySDK(text);
    }

    @android.webkit.JavascriptInterface
    public void bluetoothPrint(String content) {
        printTextByBt(content);
    }

    private void printTextBySDK(String text) {
        if (device == null) {
            device = (PrinterDevice) POSTerminal.getInstance(mContext).getDevice("cloudpos.device.printer");
        }
        try {
            device.open();
            device.printText(text);
            device.close();
        } catch (DeviceException e) {
            e.printStackTrace();
        }
    }


    private BluetoothSocket socket;
    private BluetoothDevice bluetoothDevice;
    private OutputStream outputStream;
    private InputStream inputStream;
    private byte[] readBuffer;
    private int readBufferPosition;
    private volatile boolean stopWorker;
    private String value = "";

    private void printTextByBt(String txtvalue) {
        initPrinter();
        try {
            outputStream.write(txtvalue.getBytes());
            outputStream.close();
            socket.close();
        } catch (Exception ex) {
            value += ex.toString() + "\n" + "Excep IntentPrint \n";
            Toast.makeText(mContext, value, Toast.LENGTH_LONG).show();
        }
    }

    private void initPrinter() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        try {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                mContext.startActivityForResult(enableBluetooth, 0);
            }

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    //if(device.getName().equals("SP200")) //Note, you will need to change this to match the name of your device
                    //{
                    //    bluetoothDevice = device;
                    //    break;
                    //}
                    bluetoothDevice = device;
                    break;
                }

                Method m = bluetoothDevice.getClass().getMethod("createRfcommSocket", int.class);
                socket = (BluetoothSocket) m.invoke(bluetoothDevice, 1);
                bluetoothAdapter.cancelDiscovery();
                socket.connect();
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();
                beginListenForData();
            } else {
                value += "No Devices found";
                Toast.makeText(mContext, value, Toast.LENGTH_LONG).show();
            }
        } catch (Exception ex) {
            value += ex.toString() + "\n" + " InitPrinter \n";
            Toast.makeText(mContext, value, Toast.LENGTH_LONG).show();
        }
    }

    private void beginListenForData() {
        try {
            final Handler handler = new Handler();
            // this is the ASCII code for a newline character
            final byte delimiter = 10;
            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];

            // specify US-ASCII encoding
            // tell the user data were sent to bluetooth printer device
            Thread workerThread = new Thread(new Runnable() {
                public void run() {
                    while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                        try {
                            int bytesAvailable = inputStream.available();
                            if (bytesAvailable > 0) {
                                byte[] packetBytes = new byte[bytesAvailable];
                                int read = inputStream.read(packetBytes);
                                for (int i = 0; i < bytesAvailable; i++) {
                                    byte b = packetBytes[i];
                                    if (b == delimiter) {
                                        byte[] encodedBytes = new byte[readBufferPosition];
                                        System.arraycopy(
                                                readBuffer, 0,
                                                encodedBytes, 0,
                                                encodedBytes.length
                                        );
                                        // specify US-ASCII encoding
                                        final String data = new String(encodedBytes, StandardCharsets.US_ASCII);
                                        readBufferPosition = 0;
                                        // tell the user data were sent to bluetooth printer device
                                        handler.post(new Runnable() {
                                            public void run() {
                                                Log.d("e", data);
                                            }
                                        });
                                    } else {
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }
                            }
                        } catch (IOException ex) {
                            stopWorker = true;
                        }
                    }
                }
            });
            workerThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
