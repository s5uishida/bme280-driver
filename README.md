# bme280-driver
bme280-driver is a java library that operates combined humidity, pressure and temperature sensor called [BME280](http://static.cactus.io/docs/sensors/barometric/bme280/BST-BME280_DS001-10.pdf) to connect BME280 to GPIO terminal of Raspberry Pi 3B and make it for use in java.
I releases this in the form of the Eclipse plug-in project.
You need Java 8.

I use [Pi4J](https://pi4j.com/)
for gpio communication in java and have confirmed that it works in Raspberry Pi 3B ([Raspbian Buster Lite OS](https://www.raspberrypi.org/downloads/raspbian/) (2019-07-10)).

**Note. To use Pi4J 1.2's I2C functionality, sun.misc.SharedSecrets.class is required, but this class can only be used up to Java 8 and cannot be used since Java 9. Therefore, Java 8 is required to use this library.**

## Connection of BME280 and Raspberry Pi 3B
- `Pins` of [BME280](http://static.cactus.io/docs/sensors/barometric/bme280/BST-BME280_DS001-10.pdf) p.38 "Figure 17: I2C connection diagram"
  - Vin (3.3V)
  - GND
  - SDA
  - SCL
  - SDO --> I2C address is **0x76** when connected to GND and **0x77** when connected to Vin.
- [GPIO of Raspberry Pi 3B](https://www.raspberrypi.org/documentation/usage/gpio/README.md)
  - Vin --> (1) or (17) / 3.3V
  - GND --> (6), (9), (14), (20), (25), (30), (34) or (39)
  - SDA --> (3) GPIO2 / I2C bus 1, (27) GPIO0 / I2C bus 0
  - SCL --> (5) GPIO3 / I2C bus 1, (28) GPIO1 / I2C bus 0

The connection terminals for SDA and SCL vary depending on the I2C bus number (`1` or `0`). For reference, it seems that some BME280 products are also compatible with 5V due to the voltage regulator.

## Install Raspbian Buster Lite OS (2019-07-10)
The reason for using this version is that it is the latest as of July 2019 and [BlueZ](http://www.bluez.org/) 5.50 is included from the beginning, and use Bluetooth and serial communication simultaneously.

## Configuration of Raspbian Buster Lite OS
- Edit `/boot/cmdline.txt`
```
console=serial0,115200 --> removed
```
- Edit `/boot/config.txt`
```
@@ -43,9 +43,10 @@
 #arm_freq=800
 
 # Uncomment some or all of these to enable the optional hardware interfaces
-#dtparam=i2c_arm=on
+dtparam=i2c_arm=on
+dtparam=i2c_vc=on
 #dtparam=i2s=on
-#dtparam=spi=on
+dtparam=spi=on
 
 # Uncomment this to enable the lirc-rpi module
 #dtoverlay=lirc-rpi
@@ -55,6 +56,10 @@
 # Enable audio (loads snd_bcm2835)
 dtparam=audio=on
 
+enable_uart=1
+dtoverlay=pi3-miniuart-bt
+core_freq=250
+
 [pi4]
 # Enable DRM VC4 V3D driver on top of the dispmanx display stack
 dtoverlay=vc4-fkms-v3d
```
- Edit `/etc/modules`
```
i2c-dev <-- added
```
When editing is complete, reboot.

## Install WiringPi Native Library
Pi4J depends on the [WiringPi](http://wiringpi.com/) native library by Gordon Henderson.
The Pi4J native library is dynamically linked to WiringPi.
```
# apt-get update
# apt-get install wiringpi
```
When using with Raspberry Pi 4B, install the latest version as follows.
Please refer to [here](http://wiringpi.com/wiringpi-updated-to-2-52-for-the-raspberry-pi-4b/).
```
# wget https://project-downloads.drogon.net/wiringpi-latest.deb
# dpkg -i wiringpi-latest.deb
```
Please make sure it’s version 2.52.
```
# gpio -v
gpio version: 2.52
```
**Note. In October 2019, I have not confirmed the official information that can use Raspberry Pi 4B with Pi4J and WiringPi.
I've simply checked that it works, but some problems may occur.**

## Install jdk8 on Raspberry Pi 3B
For example, the installation of OpenJDK 8 is shown below.
```
# apt-get update
# apt-get install openjdk-8-jdk
```

## Install git
If git is not included, please install it.
```
# apt-get install git
```

## Install i2c-tools
Install i2c-tools to use `i2cdetect` command.
```
# apt-get install i2c-tools
```
If BME280 is connected to I2C bus 1 and I2C address 0x76, the `i2cdetect` command displays the following.
```
# i2cdetect -y 1
     0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f
00:          -- -- -- -- -- -- -- -- -- -- -- -- -- 
10: -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- 
20: -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- 
30: -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- 
40: -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- 
50: -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- 
60: -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- -- 
70: -- -- -- -- -- -- 76 --   
```

## Use this with the following bundles
- [SLF4J 1.7.26](https://www.slf4j.org/)
- [Pi4J 1.2 (pi4j-core.jar)](https://github.com/s5uishida/pi4j-core-osgi)

I would like to thank the authors of these very useful codes, and all the contributors.

## How to use
The following sample code will be helpful. A strange value may be returned at the first time, but I think that the normal values will be obtained after the second time.
```java
import com.pi4j.io.i2c.I2CBus;

import io.github.s5uishida.iot.device.bme280.driver.BME280Driver;

public class MyBME280 {
    private static final Logger LOG = LoggerFactory.getLogger(MyBME280.class);
    
    public static void main(String[] args) {
        BME280Driver bme280 = null;
        try {
            bme280 = BME280Driver.getInstance(I2CBus.BUS_1, BME280Driver.I2C_ADDRESS_76);
            bme280.open();
            
            while (true) {
                float[] values = bme280.getSensorValues();
                LOG.info("temperature:" + values[0]);
                LOG.info("humidity:" + values[1]);
                LOG.info("pressure:" + values[2]);
                
                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {
            LOG.warn("caught - {}", e.toString());
        } catch (IOException e) {
            LOG.warn("caught - {}", e.toString());
        } finally {
            if (bme280 != null) {
                bme280.close();
            }
        }
    }
}
```
