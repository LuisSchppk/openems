package io.openems.edge.ess.api;

public interface ManagedSymmetricEssOmei extends ManagedSymmetricEss {
	public int getPowerStep();
	public boolean isReady();
	public void start();
	public int getMinSoc();
	public int getMaxSoc();
}
