package com.codahale.metrics.ruleskeeper;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

/**
 * A reporter which publishes metric values to a RulesKeeper server.
 * 
 * @see <a href="http://www.ruleskeeper.org/">RulesKeeper - Stop Guessing, Measure!</a>
 */
public class RulesKeeperReporter extends ScheduledReporter {

	private static final Logger LOGGER = LoggerFactory.getLogger(RulesKeeperReporter.class);

	private final RulesKeeper ruleskeeper;
	private final Clock clock;
	private final String prefix;

	public RulesKeeperReporter(MetricRegistry registry, RulesKeeper ruleskeeper, Clock clock, String prefix, TimeUnit rateUnit, TimeUnit durationUnit,
			MetricFilter filter) {
		super(registry, "ruleskeeper-reporter", filter, rateUnit, durationUnit);
		this.ruleskeeper = ruleskeeper;
		this.clock = clock;
		this.prefix = prefix;
	}

	/**
	 * Returns a new {@link Builder} for {@link RulesKeeperReporter}.
	 * 
	 * @param registry
	 *          the registry to report
	 * @return a {@link Builder} instance for a {@link RulesKeeperReporter}
	 */
	public static Builder forRegistry(MetricRegistry registry) {
		return new Builder(registry);
	}

	/**
	 * A builder for {@link RulesKeeperReporter} instances. Defaults to not using a prefix, using the default clock, converting rates to events/second, converting
	 * durations to milliseconds, and not filtering metrics.
	 */
	public static final class Builder {
		private final MetricRegistry registry;
		private Clock clock;
		private String prefix;
		private TimeUnit rateUnit;
		private TimeUnit durationUnit;
		private MetricFilter filter;

		private Builder(MetricRegistry registry) {
			this.registry = registry;
			this.clock = Clock.defaultClock();
			this.prefix = null;
			this.rateUnit = TimeUnit.SECONDS;
			this.durationUnit = TimeUnit.MILLISECONDS;
			this.filter = MetricFilter.ALL;
		}

		/**
		 * Use the given {@link Clock} instance for the time.
		 * 
		 * @param clock
		 *          a {@link Clock} instance
		 * @return {@code this}
		 */
		public Builder withClock(Clock clock) {
			this.clock = clock;
			return this;
		}

		/**
		 * Prefix all metric names with the given string.
		 * 
		 * @param prefix
		 *          the prefix for all metric names
		 * @return {@code this}
		 */
		public Builder prefixedWith(String prefix) {
			this.prefix = prefix;
			return this;
		}

		/**
		 * Convert rates to the given time unit.
		 * 
		 * @param rateUnit
		 *          a unit of time
		 * @return {@code this}
		 */
		public Builder convertRatesTo(TimeUnit rateUnit) {
			this.rateUnit = rateUnit;
			return this;
		}

		/**
		 * Convert durations to the given time unit.
		 * 
		 * @param durationUnit
		 *          a unit of time
		 * @return {@code this}
		 */
		public Builder convertDurationsTo(TimeUnit durationUnit) {
			this.durationUnit = durationUnit;
			return this;
		}

		/**
		 * Only report metrics which match the given filter.
		 * 
		 * @param filter
		 *          a {@link MetricFilter}
		 * @return {@code this}
		 */
		public Builder filter(MetricFilter filter) {
			this.filter = filter;
			return this;
		}

		/**
		 * Builds a {@link RulesKeeperReporter} with the given properties, sending metrics using the given {@link RulesKeeper} http client.
		 * 
		 * @param ruleskeeper
		 *          a {@link RulesKeeper} http client
		 * @return a {@link RulesKeeperReporter}
		 */
		public RulesKeeperReporter build(RulesKeeper ruleskeeper) {
			return new RulesKeeperReporter(registry, ruleskeeper, clock, prefix, rateUnit, durationUnit, filter);
		}
	}

	@Override
	public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters, SortedMap<String, Histogram> histograms,
			SortedMap<String, Meter> meters, SortedMap<String, Timer> timers) {
		final long timestamp = clock.getTime();

		try {

			for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
				reportGauge(entry.getKey(), entry.getValue(), timestamp);
			}

			for (Map.Entry<String, Counter> entry : counters.entrySet()) {
				reportCounter(entry.getKey(), entry.getValue(), timestamp);
			}

			for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
				reportHistogram(entry.getKey(), entry.getValue(), timestamp);
			}

			for (Map.Entry<String, Meter> entry : meters.entrySet()) {
				reportMetered(entry.getKey(), entry.getValue(), timestamp);
			}

			for (Map.Entry<String, Timer> entry : timers.entrySet()) {
				reportTimer(entry.getKey(), entry.getValue(), timestamp);
			}
		} catch (IllegalArgumentException e) {
			connectionError(e);
		} catch (IOException e) {
			connectionError(e);
		} catch (InterruptedException e) {
			connectionError(e);
		} catch (ExecutionException e) {
			connectionError(e);
		}
	}

	private void connectionError(Exception e) {
		LOGGER.warn("Unable to report to RulesKeeper", ruleskeeper, e);
	}

	private void reportGauge(String name, Gauge gauge, long timestamp) throws IOException, InterruptedException, ExecutionException {
		final String value = format(gauge.getValue());
		if (value != null) {
			ruleskeeper.sendSingleMetric(prefix(name), prefix(name), value, timestamp);
		}
	}

	private void reportCounter(String name, Counter counter, long timestamp) throws IOException, InterruptedException, ExecutionException {
		ruleskeeper.sendSingleMetric(prefix(name), prefix(name), format(counter.getCount()), timestamp);
	}

	private void reportHistogram(String name, Histogram histogram, long timestamp) throws IOException, InterruptedException, ExecutionException {
		final Snapshot snapshot = histogram.getSnapshot();
		ruleskeeper.send(prefix(name), "count", format(histogram.getCount()), timestamp);
		ruleskeeper.send(prefix(name), "max", format(snapshot.getMax()), timestamp);
		ruleskeeper.send(prefix(name), "mean", format(snapshot.getMean()), timestamp);
		ruleskeeper.send(prefix(name), "min", format(snapshot.getMin()), timestamp);
		ruleskeeper.send(prefix(name), "stddev", format(snapshot.getStdDev()), timestamp);
		ruleskeeper.send(prefix(name), "p50", format(snapshot.getMedian()), timestamp);
		ruleskeeper.send(prefix(name), "p75", format(snapshot.get75thPercentile()), timestamp);
		ruleskeeper.send(prefix(name), "p95", format(snapshot.get95thPercentile()), timestamp);
		ruleskeeper.send(prefix(name), "p98", format(snapshot.get98thPercentile()), timestamp);
		ruleskeeper.send(prefix(name), "p99", format(snapshot.get99thPercentile()), timestamp);
		ruleskeeper.send(prefix(name), "p999", format(snapshot.get999thPercentile()), timestamp);
	}

	private void reportMetered(String name, Metered meter, long timestamp) throws IOException, InterruptedException, ExecutionException {
		ruleskeeper.send(prefix(name), "count", format(meter.getCount()), timestamp);
		ruleskeeper.send(prefix(name), "m1_rate", format(convertRate(meter.getOneMinuteRate())), timestamp);
		ruleskeeper.send(prefix(name), "m5_rate", format(convertRate(meter.getFiveMinuteRate())), timestamp);
		ruleskeeper.send(prefix(name), "m15_rate", format(convertRate(meter.getFifteenMinuteRate())), timestamp);
		ruleskeeper.send(prefix(name), "mean_rate", format(convertRate(meter.getMeanRate())), timestamp);
	}

	private void reportTimer(String name, Timer timer, long timestamp) throws IOException, InterruptedException, ExecutionException {
		final Snapshot snapshot = timer.getSnapshot();

		ruleskeeper.send(prefix(name), "max", format(convertDuration(snapshot.getMax())), timestamp);
		ruleskeeper.send(prefix(name), "mean", format(convertDuration(snapshot.getMean())), timestamp);
		ruleskeeper.send(prefix(name), "min", format(convertDuration(snapshot.getMin())), timestamp);
		ruleskeeper.send(prefix(name), "stddev", format(convertDuration(snapshot.getStdDev())), timestamp);
		ruleskeeper.send(prefix(name), "p50", format(convertDuration(snapshot.getMedian())), timestamp);
		ruleskeeper.send(prefix(name), "p75", format(convertDuration(snapshot.get75thPercentile())), timestamp);
		ruleskeeper.send(prefix(name), "p95", format(convertDuration(snapshot.get95thPercentile())), timestamp);
		ruleskeeper.send(prefix(name), "p98", format(convertDuration(snapshot.get98thPercentile())), timestamp);
		ruleskeeper.send(prefix(name), "p99", format(convertDuration(snapshot.get99thPercentile())), timestamp);
		ruleskeeper.send(prefix(name), "p999", format(convertDuration(snapshot.get999thPercentile())), timestamp);

		reportMetered(name, timer, timestamp);
	}

	private String format(Object o) {
		if (o instanceof Float) {
			return format(((Float) o).doubleValue());
		} else if (o instanceof Double) {
			return format(((Double) o).doubleValue());
		} else if (o instanceof Byte) {
			return format(((Byte) o).longValue());
		} else if (o instanceof Short) {
			return format(((Short) o).longValue());
		} else if (o instanceof Integer) {
			return format(((Integer) o).longValue());
		} else if (o instanceof Long) {
			return format(((Long) o).longValue());
		}
		return null;
	}

	private String prefix(String... components) {
		return MetricRegistry.name(prefix, components);
	}

	private String format(long n) {
		return Long.toString(n);
	}

	private String format(double v) {
		return String.format(Locale.US, "%2.2f", v);
	}

}
