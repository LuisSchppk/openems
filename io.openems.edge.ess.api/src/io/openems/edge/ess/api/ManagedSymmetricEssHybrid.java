package io.openems.edge.ess.api;

import java.util.Collection;
import java.util.Collections;

public interface ManagedSymmetricEssHybrid extends ManagedSymmetricEss {
	
	/**
	 * Calculates upper and lower boundary of possible charge power for this cycle.
	 * Does not have to be the maximum allowed charge power.
	 * 
	 * Range in [{@link ChannelId#ALLOWED_CHARGE_POWER},0]
	 * 
	 * @return Returns upper and lower boundary of possible charge power in [W].
	 * 			{@code [0]} Minimum possible charge power.
	 * 			{@code [1]} Maximum possible charge power.
	 */
	public int[] calculatePossibleChargePower();
	
	/**
	 * Returns upper and lower boundary of possible discharge power for this cycle.
	 * Does not have to be the maximum allowed discharge power.
	 * Range in [0, {@link ChannelId#ALLOWED_CHARGE_POWER}]
	 * 
	 * @return Returns upper and lower boundary of possible discharge power in [W].
	 * 			{@code [0]} Minimum possible discharge power.
	 * 			{@code [1]} Maximum possible discharge power.
	 */
	public int[] calculatePossibleDischargePower();
}
