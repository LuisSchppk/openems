package io.openems.edge.controller.ess.omei.hybrid;

import java.lang.annotation.Annotation;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.edge.controller.ess.hybridess.Config;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String redoxId;
		private String liIonId;
		private String meterId;
		private String energyPrediction;
		private String powerPrediction;
		private int defaultMinimumEnergy;
		private int maxGridPower;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}
		
		public Builder setRedoxId(String redoxId) {
			this.redoxId=redoxId;
			return this;
		}
		
		public Builder setLiIonId(String liIonId) {
			this.liIonId = liIonId;
			return this;
		}
		
		public Builder setMeterId(String meterId) {
			this.meterId = meterId;
			return this;
		}
		
		public Builder setEnergyPrediction(String energyPrediction) {
			this.energyPrediction = energyPrediction;
			return this;
		}
		
		public Builder setPowerPrediction(String powerPrediction) {
			this.powerPrediction = powerPrediction;
			return this;
		}
		
		public Builder setDefaultMinimumEnergy(int defaultMinimumEnergy) {
			this.defaultMinimumEnergy = defaultMinimumEnergy;
			return this;
		}
		
		public Builder setMaxGridPower(int maxGridPower) {
			this.maxGridPower = maxGridPower;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 * 
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}


	@Override
	public String id() {
		return this.builder.id;
	}

	@Override
	public String redoxId() {
		return this.builder.redoxId;
	}
	
	@Override
	public String liIonId() {
		return this.builder.liIonId;
	}

	@Override
	public String meterId() {
		return this.builder.meterId;
	}

	@Override
	public String energyPrediction() {
		return this.builder.energyPrediction;
	}

	@Override
	public String powerPrediction() {
		return this.builder.powerPrediction;
	}

	@Override
	public int defaultMinimumEnergy() {
		return this.builder.defaultMinimumEnergy;
	}

	@Override
	public int maxGridPower() {
		return this.builder.maxGridPower;
	}

	
}