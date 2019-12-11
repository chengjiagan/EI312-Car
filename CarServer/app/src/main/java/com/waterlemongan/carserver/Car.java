package com.waterlemongan.carserver;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import me.zhouzhuo810.okusb.USB;

class Car {
    private USB usb;

    Car(MainActivity act) {
        usb = new USB.USBBuilder(act)
                .setBaudRate(115200)  //波特率
                .setDataBits(8)       //数据位
                .setStopBits(UsbSerialPort.STOPBITS_1) //停止位
                .setParity(UsbSerialPort.PARITY_NONE)  //校验位
                .setMaxReadBytes(20)   //接受数据最大长度
                .setReadDuration(500)  //读数据间隔时间
                .setDTR(false)    //DTR enable
                .setRTS(false)    //RTS enable
                .build();
    }

    private void write(String msg) {
        usb.writeData(msg.getBytes(), 500);
    }

    public void forward() {
        write("f");
    }

    public void backward() {
        write("b");
    }

    public void left() {
        write("l");
    }

    public void right() {
        write("r");
    }

    public void stop() {
        write("s");
    }
}
