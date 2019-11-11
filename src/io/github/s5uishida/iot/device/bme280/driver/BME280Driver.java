package io.github.s5uishida.iot.device.bme280.driver;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

/*
 * Refer to http://static.cactus.io/docs/sensors/barometric/bme280/BST-BME280_DS001-10.pdf
 *
 * @author s5uishida
 *
 */
public class BME280Driver {
	private static final Logger LOG = LoggerFactory.getLogger(BME280Driver.class);

	public static final byte I2C_ADDRESS_76 = 0x76;
	public static final byte I2C_ADDRESS_77 = 0x77;

	private static final byte DIG_T1_REG = (byte)0x88;
	private static final byte DIG_T2_REG = (byte)0x8a;
	private static final byte DIG_T3_REG = (byte)0x8c;
	private static final byte DIG_P1_REG = (byte)0x8e;
	private static final byte DIG_P2_REG = (byte)0x90;
	private static final byte DIG_P3_REG = (byte)0x92;
	private static final byte DIG_P4_REG = (byte)0x94;
	private static final byte DIG_P5_REG = (byte)0x96;
	private static final byte DIG_P6_REG = (byte)0x98;
	private static final byte DIG_P7_REG = (byte)0x9a;
	private static final byte DIG_P8_REG = (byte)0x9c;
	private static final byte DIG_P9_REG = (byte)0x9e;
	private static final byte DIG_H1_REG = (byte)0xa1;
	private static final byte DIG_H2_REG = (byte)0xe1;
	private static final byte DIG_H3_REG = (byte)0xe3;
	private static final byte DIG_H4_REG = (byte)0xe4;
	private static final byte DIG_H5_REG = (byte)0xe5;
	private static final byte DIG_H6_REG = (byte)0xe7;

	private static final byte CHIP_ID_REG	= (byte)0xd0;
	private static final byte RESET_REG		= (byte)0xe0;

	private static final byte CONTROL_HUMIDITY_REG					= (byte)0xf2;
	private static final byte CONTROL_HUMIDITY_OSRS_H_0			= (byte)0x00;
	private static final byte CONTROL_HUMIDITY_OSRS_H_1			= (byte)0x01;
	private static final byte CONTROL_HUMIDITY_OSRS_H_2			= (byte)0x02;
	private static final byte CONTROL_HUMIDITY_OSRS_H_4			= (byte)0x03;
	private static final byte CONTROL_HUMIDITY_OSRS_H_8			= (byte)0x04;
	private static final byte CONTROL_HUMIDITY_OSRS_H_16			= (byte)0x05;

	private static final byte STATUS_REG			= (byte)0xf3;
	private static final byte STATUS_MEASURING_BIT	= (byte)0x08;
	private static final byte STATUS_IM_UPDATE_BIT	= (byte)0x01;

	private static final byte CONTROL_MEASUREMENT_REG				= (byte)0xf4;
	private static final byte CONTROL_MEASUREMENT_OSRS_T_0		= (byte)0x00;
	private static final byte CONTROL_MEASUREMENT_OSRS_T_1		= (byte)0x20;
	private static final byte CONTROL_MEASUREMENT_OSRS_T_2		= (byte)0x40;
	private static final byte CONTROL_MEASUREMENT_OSRS_T_4		= (byte)0x60;
	private static final byte CONTROL_MEASUREMENT_OSRS_T_8		= (byte)0x80;
	private static final byte CONTROL_MEASUREMENT_OSRS_T_16		= (byte)0xa0;
	private static final byte CONTROL_MEASUREMENT_OSRS_P_0		= (byte)0x00;
	private static final byte CONTROL_MEASUREMENT_OSRS_P_1		= (byte)0x04;
	private static final byte CONTROL_MEASUREMENT_OSRS_P_2		= (byte)0x08;
	private static final byte CONTROL_MEASUREMENT_OSRS_P_4		= (byte)0x0c;
	private static final byte CONTROL_MEASUREMENT_OSRS_P_8		= (byte)0x10;
	private static final byte CONTROL_MEASUREMENT_OSRS_P_16		= (byte)0x14;
	private static final byte CONTROL_MEASUREMENT_SLEEP_MODE		= (byte)0x00;
	private static final byte CONTROL_MEASUREMENT_FORCED_MODE		= (byte)0x01;
	private static final byte CONTROL_MEASUREMENT_NORMAL_MODE		= (byte)0x03;

	private static final byte CONFIG_REG			= (byte)0xf5;
	private static final byte CONFIG_T_SB_0_5		= (byte)0x00;
	private static final byte CONFIG_T_SB_62_5		= (byte)0x20;
	private static final byte CONFIG_T_SB_125		= (byte)0x40;
	private static final byte CONFIG_T_SB_250		= (byte)0x60;
	private static final byte CONFIG_T_SB_500		= (byte)0x80;
	private static final byte CONFIG_T_SB_1000		= (byte)0xa0;
	private static final byte CONFIG_T_SB_10		= (byte)0xb0;
	private static final byte CONFIG_T_SB_20		= (byte)0xe0;
	private static final byte CONFIG_FILTER_OFF		= (byte)0x00;
	private static final byte CONFIG_FILTER_2		= (byte)0x04;
	private static final byte CONFIG_FILTER_4		= (byte)0x08;
	private static final byte CONFIG_FILTER_8		= (byte)0x0c;
	private static final byte CONFIG_FILTER_16		= (byte)0x10;
	private static final byte CONFIG_SPI3W			= (byte)0x01;

	private static final byte PRESSURE_DATA_REG		= (byte)0xf7;
	private static final byte TEMPERATURE_DATA_REG	= (byte)0xfa;
	private static final byte HUMIDITY_DATA_REG		= (byte)0xfd;

	private static final int CALIBRATION_DATA_LENGTH_1	 = 24;
	private static final int CALIBRATION_DATA_LENGTH_2	 = 7;

	private static final int SENSOR_DATA_LENGTH	 = 8;

	private final byte i2cAddress;
	private final I2CBus i2cBus;
	private final I2CDevice i2cDevice;
	private final String i2cName;
	private final String logPrefix;

	private final AtomicInteger useCount = new AtomicInteger(0);

	private static final ConcurrentHashMap<Integer, BME280Driver> map = new ConcurrentHashMap<Integer, BME280Driver>();

	synchronized public static BME280Driver getInstance(int i2cBusNumber, byte i2cAddress) {
		BME280Driver bme280 = map.get(i2cBusNumber);
		if (bme280 == null) {
			bme280 = new BME280Driver(i2cBusNumber, i2cAddress);
			map.put(i2cBusNumber, bme280);
		}
		return bme280;
	}

	private BME280Driver(int i2cBusNumber, byte i2cAddress) {
		if (i2cBusNumber != I2CBus.BUS_0 && i2cBusNumber != I2CBus.BUS_1) {
			throw new IllegalArgumentException("The set " + i2cBusNumber + " is not " +
					I2CBus.BUS_0 + " or " + I2CBus.BUS_1 + ".");
		}
		if (i2cAddress == I2C_ADDRESS_76 || i2cAddress == I2C_ADDRESS_77) {
			this.i2cAddress = i2cAddress;
		} else {
			throw new IllegalArgumentException("The set " + String.format("%x", i2cAddress) + " is not " +
					String.format("%x", I2C_ADDRESS_76) + " or " + String.format("%x", I2C_ADDRESS_77) + ".");
		}

		i2cName = "I2C_" + i2cBusNumber + "_" + String.format("%x", i2cAddress);
		logPrefix = "[" + i2cName + "] ";

		try {
			this.i2cBus = I2CFactory.getInstance(i2cBusNumber);
			this.i2cDevice = i2cBus.getDevice(i2cAddress);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	synchronized public void open() throws IOException {
		try {
			LOG.debug(logPrefix + "before - useCount:{}", useCount.get());
			if (useCount.compareAndSet(0, 1)) {
				readChipId();
				readSensorCoefficients();
				LOG.info(logPrefix + "opened");
			}
		} finally {
			LOG.debug(logPrefix + "after - useCount:{}", useCount.get());
		}
	}

	synchronized public void close() throws IOException {
		try {
			LOG.debug(logPrefix + "before - useCount:{}", useCount.get());
			if (useCount.compareAndSet(1, 0)) {
				i2cBus.close();
				LOG.info(logPrefix + "closed");
			}
		} finally {
			LOG.debug(logPrefix + "after - useCount:{}", useCount.get());
		}
	}

	public int getI2cBusNumber() {
		return i2cBus.getBusNumber();
	}

	public byte getI2cAddress() {
		return i2cAddress;
	}

	public String getName() {
		return i2cName;
	}

	public String getLogPrefix() {
		return logPrefix;
	}

	private void dump(byte register, byte data, String tag) {
		if (LOG.isTraceEnabled()) {
			StringBuffer sb = new StringBuffer();
			sb.append(String.format("%02x ", register));
			sb.append(String.format("%02x", data));
			LOG.trace(logPrefix + "{}{}", tag, sb.toString());
		}
	}

	private void dump(byte register, byte[] data, String tag) {
		if (LOG.isTraceEnabled()) {
			StringBuffer sb = new StringBuffer();
			sb.append(String.format("%02x ", register));
			for (byte data1 : data) {
				sb.append(String.format("%02x ", data1));
			}
			LOG.trace(logPrefix + "{}{}", tag, sb.toString().trim());
		}
	}

	private void write(byte register, byte out) throws IOException {
		try {
			dump(register, out, "BME280 sensor command: write: ");
			i2cDevice.write(register, out);
		} catch (IOException e) {
			String message = logPrefix + "failed to write.";
			LOG.warn(message);
			throw new IOException(message, e);
		}
	}

	private byte read(byte register) throws IOException {
		try {
			byte in = (byte)i2cDevice.read(register);
			dump(register, in, "BME280 sensor command: read:  ");
			return in;
		} catch (IOException e) {
			String message = logPrefix + "failed to read.";
			LOG.warn(message);
			throw new IOException(message, e);
		}
	}

	private byte[] read(byte register, int length) throws IOException {
		try {
			byte[] in = new byte[length];
			i2cDevice.read(register, in, 0, length);
			dump(register, in, "BME280 sensor command: read:  ");
			return in;
		} catch (IOException e) {
			String message = logPrefix + "failed to read.";
			LOG.warn(message);
			throw new IOException(message, e);
		}
	}

	private void readChipId() throws IOException {
		byte chipId = read(CHIP_ID_REG);
		if (chipId != (byte)0x60) {
			String message = logPrefix + "Chip ID[" + String.format("%x", chipId)  + "] is not 0x60.";
			LOG.warn(message);
			throw new IllegalStateException(message);
		}
	}

	private int dig_T1;
	private int dig_T2;
	private int dig_T3;
	private int dig_P1;
	private int dig_P2;
	private int dig_P3;
	private int dig_P4;
	private int dig_P5;
	private int dig_P6;
	private int dig_P7;
	private int dig_P8;
	private int dig_P9;
	private int dig_H1;
	private int dig_H2;
	private int dig_H3;
	private int dig_H4;
	private int dig_H5;
	private int dig_H6;

	private int signed16Bits(byte[] data, int offset) {
		int byte0 = data[offset] & 0xff;
		int byte1 = (int)data[offset + 1];

		return (byte1 << 8) + byte0;
	}

	private int unsigned16Bits(byte[] data, int offset) {
		int byte0 = data[offset] & 0xff;
		int byte1 = data[offset + 1] & 0xff;

		return (byte1 << 8) + byte0;
	}

	private void readSensorCoefficients() throws IOException {
		byte[] data = read(DIG_T1_REG, CALIBRATION_DATA_LENGTH_1);

		dig_T1 = unsigned16Bits(data, 0);
		dig_T2 = signed16Bits(data, 2);
		dig_T3 = signed16Bits(data, 4);

		dig_P1 = unsigned16Bits(data, 6);
		dig_P2 = signed16Bits(data, 8);
		dig_P3 = signed16Bits(data, 10);
		dig_P4 = signed16Bits(data, 12);
		dig_P5 = signed16Bits(data, 14);
		dig_P6 = signed16Bits(data, 16);
		dig_P7 = signed16Bits(data, 18);
		dig_P8 = signed16Bits(data, 20);
		dig_P9 = signed16Bits(data, 22);

		dig_H1 = read(DIG_H1_REG) & 0xff;

		data = read(DIG_H2_REG, CALIBRATION_DATA_LENGTH_2);

		dig_H2 = signed16Bits(data, 0);
		dig_H3 = data[2] & 0xff;
		dig_H4 = ((data[3] & 0xff) << 4) + (data[4] & 0x0f);
		dig_H5 = ((data[5] & 0xff) << 4) + ((data[4] & 0xff) >> 4);
		dig_H6 = data[6];
	}

	public float[] getSensorValues() throws IOException {
		write(CONTROL_HUMIDITY_REG, CONTROL_HUMIDITY_OSRS_H_1);
		write(CONTROL_MEASUREMENT_REG,
				(byte)(CONTROL_MEASUREMENT_OSRS_T_1 | CONTROL_MEASUREMENT_OSRS_P_1 | CONTROL_MEASUREMENT_FORCED_MODE));
		write(CONFIG_REG, CONFIG_T_SB_0_5);

		byte[] data = read(PRESSURE_DATA_REG, SENSOR_DATA_LENGTH);

		int adc_P = (((int)(data[0] & 0xff) << 16) + ((int)(data[1] & 0xff) << 8) + ((int)(data[2] & 0xff))) >> 4;
		int adc_T = (((int)(data[3] & 0xff) << 16) + ((int)(data[4] & 0xff) << 8) + ((int)(data[5] & 0xff))) >> 4;
		int adc_H = ((int)(data[6] & 0xff) << 8) + ((int)(data[7] & 0xff));

		// Temperature
		int varT1 = ((((adc_T >> 3) - (dig_T1 << 1))) * (dig_T2)) >> 11;
		int varT2 = (((((adc_T >> 4) - dig_T1) * ((adc_T >> 4) - dig_T1)) >> 12) * dig_T3) >> 14;
		int t_fine = varT1 + varT2;
		float temperature = ((t_fine * 5 + 128) >> 8) / 100F;

		// Pressure
		long varP1 = (long)t_fine - 128000;
		long varP2 = varP1 * varP1 * (long)dig_P6;
		varP2 += ((varP1 * (long)dig_P5) << 17);
		varP2 += (((long)dig_P4) << 35);
		varP1 = ((varP1 * varP1 * (long)dig_P3) >> 8) + ((varP1 * (long)dig_P2) << 12);
		varP1 = (((((long)1) << 47) + varP1)) * ((long)dig_P1) >> 33;

		float pressure;
		if (varP1 == 0) {
			pressure = 0F;
		} else {
			long p = 1048576 - adc_P;
			p = (((p << 31) - varP2) * 3125) / varP1;
			varP1 = (((long)dig_P9) * (p >> 13) * (p >> 13)) >> 25;
		varP2 = (((long)dig_P8) * p) >> 19;
		pressure = (((p + varP1 + varP2) >> 8) + (((long)dig_P7) << 4)) / 256F / 100F;
		}

		// Humidity
		int v_x1_u32r= t_fine - 76800;
		v_x1_u32r = (((((adc_H << 14) - (dig_H4 << 20) - (dig_H5 * v_x1_u32r)) +
				16384) >> 15) * (((((((v_x1_u32r * dig_H6) >> 10) * (((v_x1_u32r * dig_H3) >> 11) + 32768)) >> 10) +
						2097152) * dig_H2 + 8192) >> 14));
		v_x1_u32r -= (((((v_x1_u32r >> 15) * (v_x1_u32r >> 15)) >> 7) * dig_H1) >> 4);
		v_x1_u32r = (v_x1_u32r < 0) ? 0 : v_x1_u32r;
		v_x1_u32r = (v_x1_u32r > 419430400) ? 419430400 : v_x1_u32r;
		float humidity = (v_x1_u32r >> 12) / 1024F;

		float[] ret = new float[3];
		ret[0] = temperature;
		ret[1] = humidity;
		ret[2] = pressure;

		return ret;
	}

	/******************************************************************************************************************
	 * Sample main
	 ******************************************************************************************************************/
	public static void main(String[] args) throws IOException {
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
