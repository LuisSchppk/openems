package io.openems.edge.controller.ess.omei.hybrid;

import java.lang.annotation.Annotation;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.edge.controller.ess.hybridess.Config;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
//		private String setting0;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

//		public Builder setSetting0(String setting0) {
//			this.setting0 = setting0;
//			return this;
//		}

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
	public Class<? extends Annotation> annotationType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String id() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String alias() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean enabled() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String webconsole_configurationFactory_nameHint() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String redox_id() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String litIon_id() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String meter_id() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int minSoc() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int maxSoc() {
		// TODO Auto-generated method stub
		return 0;
	}

//	@Override
//	public String setting0() {
//		return this.builder.setting0;
//	}

}