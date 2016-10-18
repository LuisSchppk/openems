package io.openems.impl.protocol.modbus;

import java.math.BigInteger;

import io.openems.api.channel.Channel;

public class ModbusChannel extends Channel {
	public ModbusChannel(String unit, BigInteger minValue, BigInteger maxValue, BigInteger multiplier,
			BigInteger delta) {
		super(unit, minValue, maxValue, multiplier, delta);
	}

	@Override
	protected void updateValue(BigInteger value) {
		super.updateValue(value);
	}
}
